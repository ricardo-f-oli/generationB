package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.support.PlatformApiStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** The Meta Graph transport, against a real socket. */
class MetaGraphClientTest {

    private PlatformApiStub stub;
    private MetaGraphClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new PlatformApiStub();
        client = new MetaGraphClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", stub.metaBaseUrl());
        ReflectionTestUtils.setField(client, "apiVersion", "v24.0");
        ReflectionTestUtils.setField(client, "accessToken", "secret-token");
        ReflectionTestUtils.setField(client, "igUserId", PlatformApiStub.IG_USER_ID);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 0L);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("Business Discovery reads a public profile, with the token in a header")
    void businessDiscoveryUsesHeaderAuth() {
        var profile = client.businessDiscovery("@stubcreator").orElseThrow();

        assertEquals("stubcreator", profile.path("username").asText());
        assertEquals(110848, profile.path("followers_count").asInt());
        assertEquals("Bearer secret-token", stub.authorizationHeaders().get(0));
        assertFalse(stub.requests().get(0).contains("secret-token"),
                "the token must not appear in the URL: " + stub.requests().get(0));
        assertTrue(stub.requests().get(0).startsWith("/v24.0/"));
    }

    @Test
    @DisplayName("a handle that could alter the field expansion is refused before any call")
    void injectedHandlesAreRefused() {
        assertTrue(client.businessDiscovery("x){id}").isEmpty());
        assertTrue(client.businessDiscoveryMedia("a,b", 10).isEmpty());
        assertTrue(client.businessDiscovery("way_too_long_for_an_instagram_username").isEmpty());
        assertTrue(stub.requests().isEmpty(), "nothing may be sent for an invalid handle");
    }

    @Test
    @DisplayName("the leading @ is trimmed before a handle is validated")
    void theAtSignIsTrimmedFirst() {
        assertEquals("stub.creator_1", MetaGraphClient.handleOf("  @stub.creator_1 "));
        assertNull(MetaGraphClient.handleOf("@"));
        assertNull(MetaGraphClient.handleOf("stub creator"));
    }

    @Test
    @DisplayName("an error response is empty rather than an exception")
    void errorsAreEmpty() {
        stub.failNext("/" + PlatformApiStub.IG_USER_ID, 400);
        assertTrue(client.businessDiscovery("stubcreator").isEmpty());
    }

    @Test
    @DisplayName("without credentials nothing is called")
    void unconfiguredDoesNothing() {
        ReflectionTestUtils.setField(client, "accessToken", "");
        assertFalse(client.isEnabled());
        assertTrue(client.businessDiscovery("stubcreator").isEmpty());
        assertTrue(stub.requests().isEmpty());
    }
}
