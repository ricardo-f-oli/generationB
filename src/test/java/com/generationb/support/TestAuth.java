package com.generationb.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Signs in through the real login endpoint and hands back a bearer token.
 *
 * <p>Deliberately not a mocked SecurityContext: these tests are meant to prove the whole request
 * path works, and a mocked principal would skip the JWT filter and the brand-context resolution
 * that most of the interesting bugs live in.
 */
@Component
public class TestAuth {

    public static final String ADMIN = "admin@generationb.dev";
    public static final String DIRECTOR = "director@generationb.dev";
    public static final String ACCOUNT_MANAGER = "am@generationb.dev";
    public static final String PASSWORD = "Password123!";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String tokenFor(MockMvc mockMvc, String identifier) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
        Object data = parsed.get("data");
        if (!(data instanceof Map<?, ?> map) || map.get("accessToken") == null) {
            throw new IllegalStateException("Login failed for " + identifier + ": " + body);
        }
        return (String) map.get("accessToken");
    }

    public String bearer(MockMvc mockMvc, String identifier) throws Exception {
        return "Bearer " + tokenFor(mockMvc, identifier);
    }
}
