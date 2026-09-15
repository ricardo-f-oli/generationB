package com.generationb.creators;

import com.generationb.support.IntegrationTest;
import com.generationb.support.PlatformApiStub;
import com.generationb.support.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Creator data from the free platform APIs, end to end: HTTP request → controller → service →
 * provider → client → a stubbed Graph / YouTube API over a real socket → database.
 *
 * <p>What only this level proves is that the pieces are wired together: that configuring a Meta
 * token swaps the mock out, that a refresh writes the figures the rest of the app reads, that a
 * clipped post reaches the coverage table with the right columns, and that attribution to a
 * campaign only happens for posts that carry its tag.
 */
@TestPropertySource(properties = {
        "insights.meta.access-token=integration-test-token",
        "insights.meta.ig-user-id=" + PlatformApiStub.IG_USER_ID,
        "insights.meta.min-request-interval-ms=0",
        "insights.youtube.api-key=integration-test-key",
        "insights.enrichment.ttl-hours=20",
})
class PlatformInsightsIntegrationTest extends IntegrationTest {

    private static final PlatformApiStub STUB;

    static {
        try {
            STUB = new PlatformApiStub();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void platformProperties(DynamicPropertyRegistry registry) {
        registry.add("insights.meta.base-url", STUB::metaBaseUrl);
        registry.add("insights.youtube.base-url", STUB::youtubeBaseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    private String adminToken;

    @BeforeEach
    void signIn() throws Exception {
        STUB.reset();
        adminToken = auth.tokenFor(mockMvc, TestAuth.ADMIN);
    }

    // =====================================================================
    // Wiring
    // =====================================================================

    @Test
    @DisplayName("configured credentials are reported as live sources")
    void sourcesAreLive() throws Exception {
        mockMvc.perform(get("/api/creators/profile-refresh/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.live").value(true))
                .andExpect(jsonPath("$.data.instagram").value(true))
                .andExpect(jsonPath("$.data.youtube").value(true));
    }

    @Test
    @DisplayName("the Modash discovery endpoints are gone")
    void discoveryEndpointsAreGone() throws Exception {
        mockMvc.perform(get("/api/creators/discovery/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    // =====================================================================
    // #26 — refreshing existing creators without their permission
    // =====================================================================

    @Test
    @DisplayName("a refresh writes follower count and bio from the public Instagram profile")
    void refreshWritesPublicProfile() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(true));

        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followersCount").value(110848))
                .andExpect(jsonPath("$.data.bio").value("Skincare and slow mornings. London."))
                // Calculated by us from the two recent posts: (77+32)/110848 and (53+16)/110848,
                // averaged, as a percentage.
                .andExpect(jsonPath("$.data.erPercentage").value(0.08))
                // Provenance, so a measured figure is distinguishable from a typed one.
                .andExpect(jsonPath("$.data.insightsSource").value("INSTAGRAM_PUBLIC"))
                .andExpect(jsonPath("$.data.insightsRefreshedAt").isNotEmpty())
                // Demographics are never public; nothing may be invented for them.
                .andExpect(jsonPath("$.data.ukAudiencePct").doesNotExist())
                .andExpect(jsonPath("$.data.qualityBand").doesNotExist());
    }

    @Test
    @DisplayName("the token travels in a header, never in the URL")
    void credentialsAreNotInTheUrl() throws Exception {
        String creatorId = createCreator("INSTAGRAM", "stubchannel" + STUB.nonce());

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(true));

        assertTrue(STUB.requests().stream().noneMatch(r -> r.contains("integration-test-")),
                "a credential appeared in a request URL: " + STUB.requests());
        assertTrue(STUB.authorizationHeaders().contains("Bearer integration-test-token"));
        assertTrue(STUB.apiKeyHeaders().contains("integration-test-key"));
    }

    @Test
    @DisplayName("a YouTube-first creator takes subscribers as their follower count")
    void youtubeCreatorsUseSubscribers() throws Exception {
        String creatorId = createCreator("YOUTUBE", "stubchannel" + STUB.nonce());

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(true));

        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.followersCount").value(54000))
                // (3100 likes + 142 comments) / 28900 views.
                .andExpect(jsonPath("$.data.erPercentage").value(11.22))
                .andExpect(jsonPath("$.data.insightsSource").value("YOUTUBE_PUBLIC"));

        // The primary handle is a YouTube handle here; looking it up on Instagram would find a
        // stranger.
        assertEquals(0, STUB.countRequests("/" + PlatformApiStub.IG_USER_ID));
    }

    @Test
    @DisplayName("a TikTok-first creator has no free source and is skipped, not blanked")
    void tiktokCreatorsAreSkipped() throws Exception {
        String creatorId = createCreator("TIKTOK", null);

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(false))
                .andExpect(jsonPath("$.data.reason", containsString("TikTok")));

        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.followersCount").value(1234));
    }

    @Test
    @DisplayName("a second refresh inside the freshness window does not call Instagram again")
    void refreshIsSkippedWhileFresh() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(true));
        long afterFirst = STUB.countRequests("/" + PlatformApiStub.IG_USER_ID);

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(false))
                .andExpect(jsonPath("$.data.reason", containsString("still within")));
        assertEquals(afterFirst, STUB.countRequests("/" + PlatformApiStub.IG_USER_ID));

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(true));
        assertTrue(STUB.countRequests("/" + PlatformApiStub.IG_USER_ID) > afterFirst,
                "force must actually re-fetch");
    }

    @Test
    @DisplayName("a platform failure leaves the existing figures alone")
    void aFailedLookupDoesNotBlankWhatWeHave() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);

        STUB.failNext("/" + PlatformApiStub.IG_USER_ID, 400);
        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(false))
                .andExpect(jsonPath("$.data.reason", containsString("Instagram returned nothing")));

        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.followersCount").value(1234))
                .andExpect(jsonPath("$.data.insightsSource").doesNotExist());
    }

    // =====================================================================
    // #10 — auto-clipping
    // =====================================================================

    @Test
    @DisplayName("clipping writes real posts, and no view count where the platform has none")
    void clippingStoresPostsWithoutInventingViews() throws Exception {
        String creatorId = createCreator("INSTAGRAM", "stubchannel" + STUB.nonce());

        mockMvc.perform(post("/api/coverage/clip")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"creatorId\":\"" + creatorId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured", greaterThan(0)));

        mockMvc.perform(get("/api/coverage/log?creatorId=" + creatorId + "&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // Business Discovery gives no view count. Null, not zero, which would tell a
                // client the post was not seen.
                .andExpect(jsonPath("$.data[?(@.platform == 'INSTAGRAM')].views", everyItem(nullValue())))
                .andExpect(jsonPath("$.data[?(@.platform == 'INSTAGRAM')]", not(empty())))
                // YouTube does, so those rows carry the real figure.
                .andExpect(jsonPath("$.data[?(@.platform == 'YOUTUBE')].views", hasItem(28900)))
                // Reach: the follower count Instagram posts were published to, and the real views
                // YouTube reports.
                .andExpect(jsonPath("$.data[?(@.platform == 'INSTAGRAM')].reach", everyItem(is(110848))))
                .andExpect(jsonPath("$.data[?(@.platform == 'YOUTUBE')].reach", hasItem(28900)));
    }

    @Test
    @DisplayName("clipping twice does not log the same post twice")
    void clippingIsIdempotentByUrl() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);
        String body = "{\"creatorId\":\"" + creatorId + "\"}";

        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.data.captured", greaterThan(0)))
                .andExpect(jsonPath("$.data.duplicates").value(0));

        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured").value(0))
                .andExpect(jsonPath("$.data.duplicates", greaterThan(0)));
    }

    @Test
    @DisplayName("reels and carousels are told apart, under one canonical URL form")
    void reelsAndCarouselsAreDistinguished() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);

        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"creatorId\":\"" + creatorId + "\"}"));

        mockMvc.perform(get("/api/coverage/log?creatorId=" + creatorId + "&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                // Meta's /reel/ permalinks are normalised to /p/, the form every source can
                // produce, so switching from the interim source to Meta never logs a post twice.
                .andExpect(jsonPath("$.data[?(@.postType == 'REEL')].url",
                        everyItem(startsWith("https://www.instagram.com/p/"))))
                .andExpect(jsonPath("$.data[?(@.postType == 'REEL')]", not(empty())))
                .andExpect(jsonPath("$.data[?(@.postType == 'CAROUSEL')].url",
                        everyItem(startsWith("https://www.instagram.com/p/"))))
                .andExpect(jsonPath("$.data[?(@.postType == 'CAROUSEL')]", not(empty())));
    }

    // =====================================================================
    // Attribution without spending a hashtag lookup
    // =====================================================================

    @Test
    @DisplayName("only the post carrying the campaign tag is credited to the campaign")
    void onlyTaggedPostsAreAttributed() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);
        String campaignId = createCampaign("Summer Seeding");
        String tag = trackingHashtagOf(campaignId);

        // Fetched BY HANDLE, so attribution is exact — and it costs none of the 30 weekly tags.
        STUB.withCampaignTag(tag);

        mockMvc.perform(post("/api/coverage/campaigns/" + campaignId + "/clip")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"creatorIds\":[\"" + creatorId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured", greaterThan(0)));

        mockMvc.perform(get("/api/coverage/log?campaignId=" + campaignId + "&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].unsolicited").value(false))
                .andExpect(jsonPath("$.data[0].matchedTerm").value("#" + tag));

        assertEquals(0, STUB.countRequests("ig_hashtag_search"),
                "attribution must not spend the hashtag allowance");
    }

    @Test
    @DisplayName("a hand-logged Instagram post is linked to the creator and gets reach from followers")
    void manualInstagramPostsGetReach() throws Exception {
        String creatorId = createCreator("INSTAGRAM", null);
        String handle = JsonPath.read(mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(), "$.data.handle");
        String campaignId = createCampaign("Manual Logging");
        String tag = trackingHashtagOf(campaignId);

        mockMvc.perform(post("/api/coverage/log")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"creatorHandle":"@%s","platform":"INSTAGRAM","postType":"REEL",
                                 "url":"https://www.instagram.com/reel/Manual%s/?igsh=abc",
                                 "campaignId":"%s","caption":"Loving it #%s",
                                 "likes":100,"comments":23}
                                """.formatted(handle, STUB.nonce(), campaignId, tag)))
                .andExpect(status().isOk())
                // No views on Instagram, so reach is the creator's follower count (1234).
                .andExpect(jsonPath("$.data.reach").value(1234))
                .andExpect(jsonPath("$.data.views").doesNotExist())
                .andExpect(jsonPath("$.data.creatorId").value(creatorId))
                .andExpect(jsonPath("$.data.campaignId").value(campaignId))
                .andExpect(jsonPath("$.data.matchedTerm").value("#" + tag))
                // (100 + 23) / 1234 followers.
                .andExpect(jsonPath("$.data.er").value(9.97))
                .andExpect(jsonPath("$.data.url").value("https://www.instagram.com/p/Manual" + STUB.nonce() + "/"));

        // The same post pasted again with its /p/ link is recognised as already logged.
        mockMvc.perform(post("/api/coverage/log")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"creatorHandle":"%s","url":"https://www.instagram.com/p/Manual%s/"}
                                """.formatted(handle, STUB.nonce())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("every campaign gets its own tracking tag")
    void campaignsGetDistinctTrackingTags() throws Exception {
        String first = trackingHashtagOf(createCampaign("Summer Seeding"));
        String second = trackingHashtagOf(createCampaign("Summer Seeding"));

        assertNotEquals(first, second);
        assertTrue(first.matches("[a-z0-9]+"), "a hashtag may only contain letters and digits: " + first);
        assertTrue(first.length() >= 6, "too short to be unambiguous: " + first);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** A fresh creator per test, so tests sharing one database never collide on a handle. */
    private String createCreator(String platform, String youtubeHandle) throws Exception {
        String handle = "stubcreator" + STUB.nonce().toLowerCase() + System.nanoTime() % 100000;
        String response = mockMvc.perform(post("/api/creators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"name":"Stub Creator","handle":"%s","primaryPlatform":"%s",
                                 "followersCount":1234%s}
                                """.formatted(handle, platform,
                                youtubeHandle == null ? "" : ",\"youtubeHandle\":\"" + youtubeHandle + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String createCampaign(String name) throws Exception {
        String response = mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"campaignType\":\"SEEDING\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String trackingHashtagOf(String campaignId) throws Exception {
        String response = mockMvc.perform(get("/api/campaigns/" + campaignId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.trackingHashtag");
    }
}
