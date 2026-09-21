package com.generationb.foundation;

import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rule this file exists to protect: an account on the seeded password can log in, and can
 * then do nothing at all except replace it.
 *
 * <p>The distinction that matters is between the flag and the gate. A `mustChangePassword` field
 * that only the frontend honours is not a control — the login returns a working access token,
 * and the password it was obtained with is printed in the repository's README. So the assertion
 * here is made against the API directly, with the token the login handed back, exactly as
 * someone skipping the UI would.
 *
 * <p>Restores the seeded password afterwards: {@link TestAuth} signs every other integration
 * test in as this account, and leaving it changed would fail all of them.
 */
@DisplayName("Forced password change")
class ForcedPasswordChangeIntegrationTest extends IntegrationTest {

    /** BCrypt (cost 10) of Password123!, as seeded by V15 and reasserted by V44. */
    private static final String SEEDED_HASH =
            "$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta";

    private static final String NEW_PASSWORD = "correct-horse-battery-staple";

    @AfterEach
    void restoreTheSeededAccount() {
        jdbcTemplate.update(
                "UPDATE users SET password = ?, must_change_password = false WHERE email = ?",
                SEEDED_HASH, TestAuth.ADMIN);
    }

    private void flagAdmin() {
        jdbcTemplate.update(
                "UPDATE users SET password = ?, must_change_password = true WHERE email = ?",
                SEEDED_HASH, TestAuth.ADMIN);
    }

    @Test
    @DisplayName("the seeded password still logs in, and says a change is required")
    void loginSucceedsAndAdvertisesTheFlag() throws Exception {
        flagAdmin();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(TestAuth.ADMIN, TestAuth.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("that token cannot reach the rest of the API")
    void everythingElseIsRefused() throws Exception {
        flagAdmin();
        String bearer = auth.bearer(mockMvc, TestAuth.ADMIN);

        // The creator list stands in for "any authenticated endpoint at all".
        mockMvc.perform(get("/api/creators").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("the current password has to be right, and the new one has to be different")
    void theChangeItselfIsValidated() throws Exception {
        flagAdmin();
        String bearer = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"not-the-password","newPassword":"%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest());

        // Reusing the seeded password would clear the flag while leaving the account on exactly
        // the credential this whole mechanism exists to retire.
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(TestAuth.PASSWORD, TestAuth.PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changing it opens the API, and the seeded password stops working")
    void changingThePasswordLiftsTheGate() throws Exception {
        flagAdmin();
        String bearer = auth.bearer(mockMvc, TestAuth.ADMIN);

        String body = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(TestAuth.PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();

        // The response carries a fresh pair precisely because the change revoked the old one.
        String newBearer = "Bearer " + com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");

        mockMvc.perform(get("/api/creators").header("Authorization", newBearer))
                .andExpect(status().isOk());

        // And the credential from the README is now worthless.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(TestAuth.ADMIN, TestAuth.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
