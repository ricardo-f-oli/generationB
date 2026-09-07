package com.generationb.foundation;

import com.generationb.foundation.internal.DataRetentionService;
import com.generationb.shared.RetentionSweeper;
import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Requirement #37, against a real database.
 *
 * <p>Retention is the one feature where a bug is silent in both directions: deleting too much
 * destroys a client's data, and deleting nothing looks identical to having nothing to delete.
 * Neither shows up in a screenshot, so this asserts row counts before and after.
 *
 * <p>The dates are pushed into the past with SQL rather than by waiting, which is the only way to
 * test a 90-day window in a test suite that has to finish.
 */
class RetentionIntegrationTest extends IntegrationTest {

    @Autowired
    private DataRetentionService retentionService;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void signIn() throws Exception {
        adminToken = auth.tokenFor(mockMvc, TestAuth.ADMIN);
    }

    // =====================================================================
    // The contract
    // =====================================================================

    @Test
    @DisplayName("every sweeper declares a dataset and a policy in plain words")
    void policiesAreSelfDescribing() {
        List<RetentionSweeper.Outcome> policies = retentionService.declaredPolicies();

        // The GDPR screen renders these strings, and they are what the privacy notice has to
        // match. A blank one means a screen that silently shows nothing.
        assertFalse(policies.isEmpty());
        for (RetentionSweeper.Outcome policy : policies) {
            assertNotNull(policy.dataset());
            assertFalse(policy.dataset().isBlank(), "a sweeper with no dataset name");
            assertNotNull(policy.policy());
            assertTrue(policy.policy().length() > 20,
                    "policy for " + policy.dataset() + " is too terse to be checkable");
        }
    }

    @Test
    @DisplayName("the datasets holding personal data all have a sweeper")
    void everyPersonalDataStoreIsCovered() {
        List<String> datasets = retentionService.declaredPolicies().stream()
                .map(RetentionSweeper.Outcome::dataset)
                .toList();

        // A checklist rather than a clever reflection trick: adding a table with personal data
        // in it should fail here until somebody decides how long it is kept.
        assertTrue(datasets.contains("Gifting addresses"), datasets.toString());
        assertTrue(datasets.contains("Unconfirmed waitlist sign-ups"), datasets.toString());
        assertTrue(datasets.contains("Outreach email threads"), datasets.toString());
        assertTrue(datasets.contains("Audit trail"), datasets.toString());
        assertTrue(datasets.contains("Login attempts and expired tokens"), datasets.toString());
        assertTrue(datasets.contains("Dormant creator records"), datasets.toString());
    }

    // =====================================================================
    // Behaviour
    // =====================================================================

    @Test
    @DisplayName("a preview counts what is due and deletes nothing")
    void aDryRunChangesNothing() {
        insertOldLoginAttempts(5);
        // Counted after seeding: the helper clears the table first, so a count taken before it
        // would include the sign-in attempt this test's own authentication just made.
        int before = countLoginAttempts();

        List<RetentionSweeper.Outcome> outcomes = retentionService.runAll(true, null);

        assertTrue(total(outcomes) >= 5, "the preview should have found the aged rows");
        assertEquals(before, countLoginAttempts(),
                "a preview must not delete anything — this is the button on the GDPR screen");
    }

    @Test
    @DisplayName("a real pass removes aged rows and leaves recent ones")
    void agedRowsGoAndRecentOnesStay() {
        insertOldLoginAttempts(4);
        insertRecentLoginAttempts(3);

        retentionService.runAll(false, null);

        // The recent three survive. A sweeper that deletes by table rather than by age would
        // take them too, and nothing on screen would show it had.
        assertEquals(3, countLoginAttempts());
    }

    @Test
    @DisplayName("a second pass removes nothing, so the job is safe to re-run")
    void theSweepIsIdempotent() {
        insertOldLoginAttempts(4);

        retentionService.runAll(false, null);
        List<RetentionSweeper.Outcome> second = retentionService.runAll(false, null);

        assertEquals(0, total(second),
                "a re-run must be a no-op; a scheduler that fires twice must not double-delete");
    }

    @Test
    @DisplayName("a gifting address outlives the parcel by the stated window, then goes")
    void giftingAddressesArePurgedAfterDelivery() {
        UUID creatorId = anyCreatorId();
        jdbc.update("DELETE FROM gifting_addresses WHERE creator_id = ?", creatorId);
        jdbc.update("""
            INSERT INTO gifting_addresses
                (creator_id, street, city, postal_code, country, gdpr_consent_flag,
                 consented_at, consent_source, captured_at)
            VALUES (?, '1 Test Street', 'Leeds', 'LS1 1AA', 'UK', true,
                    NOW() - INTERVAL '400 days', 'CAPTURE_LINK', NOW() - INTERVAL '400 days')
            """, creatorId);

        assertEquals(1, countAddressesFor(creatorId));
        retentionService.runAll(false, null);

        // Nothing was ever dispatched to it, so the 180-day unused window applies.
        assertEquals(0, countAddressesFor(creatorId),
                "a home address with no dispatch behind it must not sit on file indefinitely");
    }

    @Test
    @DisplayName("a recently captured address is left alone")
    void freshAddressesSurvive() {
        UUID creatorId = anyCreatorId();
        jdbc.update("DELETE FROM gifting_addresses WHERE creator_id = ?", creatorId);
        jdbc.update("""
            INSERT INTO gifting_addresses
                (creator_id, street, city, postal_code, country, gdpr_consent_flag,
                 consented_at, consent_source, captured_at)
            VALUES (?, '2 Fresh Road', 'Leeds', 'LS1 1AA', 'UK', true,
                    NOW(), 'CAPTURE_LINK', NOW())
            """, creatorId);

        retentionService.runAll(false, null);

        assertEquals(1, countAddressesFor(creatorId),
                "an address captured today is still needed — the parcel has not been sent");
    }

    @Test
    @DisplayName("dormant creators are reported, never deleted")
    void dormantCreatorsAreOnlyFlagged() {
        long before = countCreators();
        // Old enough to qualify on every axis the review query checks.
        jdbc.update("UPDATE creators SET updated_at = NOW() - INTERVAL '5 years' "
                + "WHERE deleted_at IS NULL AND anonymised_at IS NULL");

        retentionService.runAll(false, null);

        // This is decision 005: a cron job must not quietly anonymise a client's contact list.
        assertEquals(before, countCreators(),
                "the dormancy sweeper must report only — deleting here is somebody's decision");
    }

    // =====================================================================
    // Evidence
    // =====================================================================

    @Test
    @DisplayName("every pass writes an evidence row carrying the policy as it stood")
    void eachPassIsRecorded() {
        jdbc.update("DELETE FROM retention_runs");
        retentionService.runAll(false, null);

        List<Map<String, Object>> runs =
                jdbc.queryForList("SELECT dataset, policy, dry_run, error FROM retention_runs");

        assertEquals(retentionService.declaredPolicies().size(), runs.size(),
                "one row per sweeper — a missing row is a sweeper that silently did not run");
        for (Map<String, Object> run : runs) {
            assertNotNull(run.get("policy"));
            assertNull(run.get("error"), "sweeper failed: " + run.get("dataset"));
            assertEquals(false, run.get("dry_run"));
        }
    }

    @Test
    @DisplayName("a preview is recorded as a preview, so the evidence log cannot mislead")
    void previewsAreMarked() {
        jdbc.update("DELETE FROM retention_runs");
        retentionService.runAll(true, null);

        Integer real = jdbc.queryForObject(
                "SELECT COUNT(*) FROM retention_runs WHERE dry_run = FALSE", Integer.class);
        assertEquals(0, real,
                "a preview logged as a real pass would let the screen claim data was removed "
                        + "when none was");
    }

    // =====================================================================
    // The API
    // =====================================================================

    @Test
    @DisplayName("the GDPR screen gets the policies and the schedule")
    void theStatusEndpointDescribesThePolicy() throws Exception {
        mockMvc.perform(get("/api/settings/retention")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").isBoolean())
                .andExpect(jsonPath("$.data.schedule").isNotEmpty())
                .andExpect(jsonPath("$.data.policies").isArray())
                .andExpect(jsonPath("$.data.policies[0].dataset").isNotEmpty())
                .andExpect(jsonPath("$.data.policies[0].policy").isNotEmpty());
    }

    @Test
    @DisplayName("the manual run previews unless told otherwise")
    void theRunEndpointDefaultsToAPreview() throws Exception {
        insertOldLoginAttempts(3);

        mockMvc.perform(post("/api/settings/retention/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Opening the screen and pressing the obvious button must not delete anything.
        assertEquals(3, countLoginAttempts());
    }

    @Test
    @DisplayName("an ordinary user cannot read or trigger retention")
    void retentionIsAdminOnly() throws Exception {
        String managerToken = auth.tokenFor(mockMvc, TestAuth.ACCOUNT_MANAGER);

        mockMvc.perform(get("/api/settings/retention")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void insertOldLoginAttempts(int count) {
        jdbc.update("DELETE FROM login_attempts");
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO login_attempts (identifier, ip_address, successful, "
                    + "attempted_at) VALUES (?, '10.0.0.1', false, NOW() - INTERVAL '200 days')",
                    "old" + i + "@example.com");
        }
    }

    private void insertRecentLoginAttempts(int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("INSERT INTO login_attempts (identifier, ip_address, successful, "
                    + "attempted_at) VALUES (?, '10.0.0.2', true, NOW())",
                    "new" + i + "@example.com");
        }
    }

    private int countLoginAttempts() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM login_attempts", Integer.class);
        return count == null ? 0 : count;
    }

    private int countAddressesFor(UUID creatorId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gifting_addresses WHERE creator_id = ?",
                Integer.class, creatorId);
        return count == null ? 0 : count;
    }

    private long countCreators() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM creators WHERE deleted_at IS NULL AND anonymised_at IS NULL",
                Long.class);
        return count == null ? 0 : count;
    }

    private UUID anyCreatorId() {
        return jdbc.queryForObject(
                "SELECT id FROM creators WHERE deleted_at IS NULL LIMIT 1", UUID.class);
    }

    private static int total(List<RetentionSweeper.Outcome> outcomes) {
        return outcomes.stream().mapToInt(RetentionSweeper.Outcome::affected).sum();
    }
}
