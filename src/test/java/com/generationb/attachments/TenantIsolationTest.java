package com.generationb.attachments;

import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The assertion that matters most in a multi-tenant system: one brand cannot reach another's
 * files, even holding a valid id.
 *
 * <p>This is the test that would have caught the coverage log calling {@code findAll()}. It is
 * worth having one of these per module that stores anything.
 */
@DisplayName("Tenant isolation")
class TenantIsolationTest extends IntegrationTest {

    private static final UUID CARD = UUID.fromString("e4000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_BRAND = UUID.fromString("11111111-2222-2222-2222-111111111111");
    private static final String OTHER_USER = "rival@mediheal.example";

    /** The bcrypt hash of the shared demo password, as seeded in V15. */
    private static final String PASSWORD_HASH =
            "$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createAUserOnAnotherBrand() {
        // A second tenant with its own admin. Inserted directly because the user-management API
        // deliberately cannot create a user outside the caller's own brand.
        jdbc.update("""
                INSERT INTO users (id, brand_id, name, email, password, role, active,
                                   failed_login_count, created_at, updated_at)
                VALUES (?, ?, 'Rival Admin', ?, ?, 'ADMIN', true, 0, now(), now())
                ON CONFLICT (email) DO NOTHING
                """,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                OTHER_BRAND, OTHER_USER, PASSWORD_HASH);
    }

    @Test
    @DisplayName("another brand cannot download our attachment, even with its id")
    void cannotDownloadAnotherBrandsFile() throws Exception {
        String ours = auth.bearer(mockMvc, TestAuth.ADMIN);

        String id = mockMvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "confidential.pdf",
                                "application/pdf", "client rates".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", ours))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        String theirs = auth.bearer(mockMvc, OTHER_USER);

        // The id is real and they are properly authenticated — only the brand differs.
        mockMvc.perform(get("/api/attachments/{id}/download", id)
                        .header("Authorization", theirs))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/attachments/{id}", id)
                        .header("Authorization", theirs))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("another brand's listing does not include our attachment")
    void listingIsScopedToTheBrand() throws Exception {
        String ours = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "ours.pdf",
                                "application/pdf", "x".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", ours))
                .andExpect(status().isOk());

        String theirs = auth.bearer(mockMvc, OTHER_USER);

        // Same owner id, different tenant: the list must come back empty rather than leaking.
        mockMvc.perform(get("/api/attachments")
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", theirs))
                .andExpect(status().isOk())
                // A JSONPath filter: the array must contain nothing with that name.
                .andExpect(jsonPath("$.data[?(@.filename=='ours.pdf')]").isEmpty());
    }

    @Test
    @DisplayName("the coverage log is scoped too — the bug this test exists for")
    void coverageIsScopedToTheBrand() throws Exception {
        String ours = auth.bearer(mockMvc, TestAuth.ADMIN);
        String theirs = auth.bearer(mockMvc, OTHER_USER);

        // The seed data puts coverage on brand one only.
        mockMvc.perform(get("/api/coverage/log").header("Authorization", ours))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/coverage/log").header("Authorization", theirs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }
}
