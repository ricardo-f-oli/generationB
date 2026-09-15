package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.support.PlatformApiStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Where Instagram profiles come from, and the shape every source maps onto. */
class InstagramProfilesTest {

    private PlatformApiStub stub;
    private MetaGraphClient metaClient;
    private InstagramProfiles profiles;

    @BeforeEach
    void setUp() throws Exception {
        stub = new PlatformApiStub();
        metaClient = new MetaGraphClient(new ObjectMapper());
        ReflectionTestUtils.setField(metaClient, "baseUrl", stub.metaBaseUrl());
        ReflectionTestUtils.setField(metaClient, "apiVersion", "v24.0");
        ReflectionTestUtils.setField(metaClient, "accessToken", "meta-token");
        ReflectionTestUtils.setField(metaClient, "igUserId", PlatformApiStub.IG_USER_ID);
        ReflectionTestUtils.setField(metaClient, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(metaClient, "minRequestIntervalMs", 0L);

        profiles = new InstagramProfiles(new MetaInstagramProfileSource(metaClient));
        ReflectionTestUtils.setField(profiles, "cacheMinutes", 30);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("Business Discovery is mapped onto our own shape")
    void metaProfileIsMapped() {
        InstagramProfile profile = profiles.fetch("@stubcreator", 12).orElseThrow();

        assertEquals("META", profile.source());
        assertEquals(110848, profile.followers());
        assertEquals(2, profile.posts().size());
        assertEquals("REEL", profile.posts().get(0).postType());
        assertNull(profile.posts().get(0).views(), "Business Discovery publishes no view count");
        assertTrue(profile.posts().stream().allMatch(p -> p.url().startsWith("https://www.instagram.com/p/")));
    }

    @Test
    @DisplayName("a refresh then a clip is one lookup, and a forced refresh bypasses the cache")
    void lookupsAreCached() {
        profiles.fetch("stubcreator", 12);
        profiles.fetch("STUBCREATOR", 12);
        assertEquals(1, stub.countRequests("/" + PlatformApiStub.IG_USER_ID));

        profiles.evict("stubcreator");
        profiles.fetch("stubcreator", 12);
        assertEquals(2, stub.countRequests("/" + PlatformApiStub.IG_USER_ID));
    }

    @Test
    @DisplayName("without Meta credentials there is no source and no call")
    void nothingConfigured() {
        ReflectionTestUtils.setField(metaClient, "accessToken", "");
        assertFalse(profiles.isEnabled());
        assertNull(profiles.activeName());
        assertTrue(profiles.fetch("stubcreator", 12).isEmpty());
        assertTrue(stub.requests().isEmpty());
    }

    @Test
    @DisplayName("an invalid handle never reaches a source")
    void invalidHandlesAreRefused() {
        assertTrue(profiles.fetch("x){id}", 12).isEmpty());
        assertTrue(stub.requests().isEmpty());
    }

    @Test
    @DisplayName("Instagram post links normalise to one form")
    void canonicalUrls() {
        assertEquals("https://www.instagram.com/p/Abc_1-2/",
                InstagramProfile.canonicalUrl("https://www.instagram.com/reel/Abc_1-2/?igsh=x"));
        assertEquals("https://www.instagram.com/p/Abc/",
                InstagramProfile.canonicalUrl("https://instagram.com/somebody/p/Abc/"));
        assertEquals("https://example.com/x", InstagramProfile.canonicalUrl("https://example.com/x"));
    }
}
