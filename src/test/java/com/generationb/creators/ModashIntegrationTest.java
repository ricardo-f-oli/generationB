package com.generationb.creators;

import com.generationb.support.IntegrationTest;
import com.generationb.support.ModashStub;
import com.generationb.support.ModashStub.Response;
import com.generationb.support.TestAuth;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The creator-data vendor, end to end: HTTP request → controller → service → provider → client →
 * a stubbed Modash over a real socket → database.
 *
 * <p>{@code ModashMappingTest} covers the field-by-field translation and {@code ModashClientTest}
 * covers the transport. What only this level can prove is that the pieces are wired together at
 * all: that {@code insights.provider=modash} actually swaps the mock out, that a clipped post
 * reaches the coverage table with the right columns, that an enrichment writes the demographic
 * fields the KPI matcher reads, and that the freshness guard stops the second call.
 *
 * <p>The vendor is stubbed rather than live. The account is metered, and a CI run that spends the
 * agency's credits is a bill rather than a test.
 */
@TestPropertySource(properties = {
        "insights.provider=modash",
        "insights.modash.api-key=integration-test-key",
        // The suite should not be paced by the real 550ms rate limit.
        "insights.modash.min-request-interval-ms=1",
        // Always re-read the balance. The default 120s cache would carry one test's spending
        // into the next and make the assertions order-dependent.
        "insights.modash.budget-ttl-seconds=0",
        "insights.enrichment.ttl-days=30",
})
class ModashIntegrationTest extends IntegrationTest {

    private static final ModashStub STUB;

    static {
        try {
            STUB = new ModashStub();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void modashProperties(DynamicPropertyRegistry registry) {
        registry.add("insights.modash.base-url", STUB::baseUrl);
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
    @DisplayName("the live provider replaces the mock when the vendor is selected")
    void theLiveProviderIsInUse() throws Exception {
        mockMvc.perform(get("/api/creators/discovery/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.live").value(true))
                .andExpect(jsonPath("$.data.credits").isNumber())
                .andExpect(jsonPath("$.data.rawRequests").isNumber())
                .andExpect(jsonPath("$.data.balanceUnavailable").doesNotExist());
    }

    // =====================================================================
    // #26 — audience demographics
    // =====================================================================

    @Test
    @DisplayName("enriching a creator writes the audience figures the KPI matcher reads")
    void enrichmentPopulatesDemographics() throws Exception {
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(true));

        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // 0.643 in the payload is 64.3%, and it is read from the GB entry rather than
                // the first country listed.
                .andExpect(jsonPath("$.data.ukAudiencePct").value(64.3))
                // 0.0421 is 4.21%, not 0.0421%. The report expresses this as a fraction.
                .andExpect(jsonPath("$.data.erPercentage").value(4.21))
                .andExpect(jsonPath("$.data.audienceAgeBand").value("25-34 (44.0%)"))
                .andExpect(jsonPath("$.data.audienceGenderSplit").value("Female 91.0%, Male 9.0%"))
                // 0.9012 credibility is above the 85 threshold.
                .andExpect(jsonPath("$.data.qualityBand").value("HIGH"))
                .andExpect(jsonPath("$.data.followersCount").value(110848))
                // Provenance, so a measured figure is distinguishable from a typed one.
                .andExpect(jsonPath("$.data.insightsSource").value("MODASH"))
                .andExpect(jsonPath("$.data.insightsRefreshedAt").isNotEmpty());
    }

    @Test
    @DisplayName("a second enrichment inside the freshness window does not buy another report")
    void enrichmentIsSkippedWhileFresh() throws Exception {
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(true));

        int afterFirst = reportCalls();

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(false))
                .andExpect(jsonPath("$.data.reason", containsString("still within")));

        // A report is a whole credit. Re-buying one that is 30 seconds old is money.
        org.junit.jupiter.api.Assertions.assertEquals(afterFirst, reportCalls(),
                "the freshness guard must stop the second report from being fetched");
    }

    @Test
    @DisplayName("force re-buys a report that is still fresh")
    void forceOverridesTheFreshnessGuard() throws Exception {
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                .header("Authorization", "Bearer " + adminToken));
        int afterFirst = reportCalls();

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(true));

        org.junit.jupiter.api.Assertions.assertTrue(reportCalls() > afterFirst,
                "force must actually re-fetch");
    }

    @Test
    @DisplayName("a vendor failure leaves the existing figures alone")
    void aFailedLookupDoesNotBlankWhatWeHave() throws Exception {
        Creator creator = anyCreator();
        String creatorId = creator.id();

        // Enrich once so there is something on file worth protecting.
        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich")
                .header("Authorization", "Bearer " + adminToken));

        // Now the vendor 404s — a deleted or renamed account. Enqueued for every platform
        // because the path depends on the seeded creator's primaryPlatform.
        for (String platform : new String[] {"instagram", "tiktok", "youtube"}) {
            STUB.enqueue("/" + platform + "/profile/" + creator.handle() + "/report",
                    Response.status(404));
        }

        mockMvc.perform(post("/api/creators/" + creatorId + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(false));

        // Overwriting researched demographics with nulls because the vendor had a bad day is
        // worse than a stale value.
        mockMvc.perform(get("/api/creators/" + creatorId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.ukAudiencePct").value(64.3))
                .andExpect(jsonPath("$.data.insightsSource").value("MODASH"));
    }

    // =====================================================================
    // #10 — auto-clipping
    // =====================================================================

    @Test
    @DisplayName("clipping writes real posts, and no view count where the platform has none")
    void clippingStoresPostsWithoutInventingViews() throws Exception {
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/coverage/clip")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"creatorId\":\"" + creatorId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured", greaterThan(0)));

        mockMvc.perform(get("/api/coverage/log?creatorId=" + creatorId + "&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // Instagram publishes no view count. Every Instagram row must carry null —
                // not zero, which would tell a client the campaign was not seen.
                .andExpect(jsonPath("$.data[?(@.platform == 'INSTAGRAM')].views",
                        everyItem(nullValue())))
                .andExpect(jsonPath("$.data[?(@.platform == 'INSTAGRAM')]", not(empty())))
                // TikTok does, so those rows carry the real figure.
                .andExpect(jsonPath("$.data[?(@.platform == 'TIKTOK')].views",
                        hasItem(28900)));
    }

    @Test
    @DisplayName("clipping twice does not log the same post twice")
    void clippingIsIdempotentByUrl() throws Exception {
        String creatorId = anyCreatorId();
        String body = "{\"creatorId\":\"" + creatorId + "\"}";

        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(jsonPath("$.data.captured", greaterThan(0)))
                .andExpect(jsonPath("$.data.duplicates").value(0));

        // The same feed again. Every URL is already on file.
        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured").value(0))
                .andExpect(jsonPath("$.data.duplicates", greaterThan(0)));
    }

    @Test
    @DisplayName("post type and URL follow the vendor's product type")
    void reelsAndCarouselsAreDistinguished() throws Exception {
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/coverage/clip").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"creatorId\":\"" + creatorId + "\"}"));

        mockMvc.perform(get("/api/coverage/log?creatorId=" + creatorId + "&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                // A reel lives at /reel/, a carousel at /p/. The URL is the dedupe key, so
                // getting the segment wrong logs every reel twice. Asserted as a pattern rather
                // than a literal because the stub varies the post code per test run.
                .andExpect(jsonPath("$.data[?(@.postType == 'REEL')].url",
                        everyItem(startsWith("https://www.instagram.com/reel/"))))
                .andExpect(jsonPath("$.data[?(@.postType == 'REEL')]", not(empty())))
                .andExpect(jsonPath("$.data[?(@.postType == 'CAROUSEL')].url",
                        everyItem(startsWith("https://www.instagram.com/p/"))))
                .andExpect(jsonPath("$.data[?(@.postType == 'CAROUSEL')]", not(empty())));
    }

    // =====================================================================
    // #23 / #25 — discovery
    // =====================================================================

    @Test
    @DisplayName("natural-language search returns mapped profiles")
    void naturalLanguageSearch() throws Exception {
        mockMvc.perform(post("/api/creators/discovery/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"query\":\"UK skincare reviewers\",\"platform\":\"instagram\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].handle").value("mrs_sandra_bm"))
                .andExpect(jsonPath("$.data[0].followers").value(6310))
                // AI search reports engagement rate as a percentage already. Converting it
                // again turns 1.5% into 147%.
                .andExpect(jsonPath("$.data[0].er").value(1.5));
    }

    @Test
    @DisplayName("competitor mentions group by creator and are never written to coverage")
    void competitorMentionsAreReadOnly() throws Exception {
        long before = coverageRowCount();

        mockMvc.perform(get("/api/creators/discovery/competitor-mentions?term=%23rival")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].handle").value("stubcreator"))
                .andExpect(jsonPath("$.data[0].posts", greaterThan(0)));

        // A competitor's posts are not this client's coverage. Filing them as such would
        // inflate every report the client receives.
        org.junit.jupiter.api.Assertions.assertEquals(before, coverageRowCount(),
                "competitor discovery must not write to the coverage log");
    }

    // =====================================================================
    // Degradation
    // =====================================================================

    @Test
    @DisplayName("an exhausted account refuses to clip rather than failing oddly")
    void anExhaustedAccountDeclinesCleanly() throws Exception {
        STUB.setBalance(0, 0);
        String creatorId = anyCreatorId();

        mockMvc.perform(post("/api/coverage/clip")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"creatorId\":\"" + creatorId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captured").value(0));
    }

    @Test
    @DisplayName("a vendor outage does not surface as a 500")
    void aVendorOutageIsHandled() throws Exception {
        for (int i = 0; i < 6; i++) {
            STUB.enqueue("/ai/instagram/text-search", Response.status(503));
        }

        mockMvc.perform(post("/api/creators/discovery/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"query\":\"anything\",\"platform\":\"instagram\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** A seeded creator with both platforms populated, so a clip fetches both feeds. */
    private record Creator(String id, String handle) {}

    private Creator anyCreator() throws Exception {
        String response = mockMvc.perform(get("/api/creators?size=1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.data[0].id");
        String handle = com.jayway.jsonpath.JsonPath.read(response, "$.data[0].handle");

        // Only the TikTok handle is set. Rewriting the Instagram handle would collide with the
        // unique index the moment a second test in this class did the same thing — these tests
        // share one database. The stub answers for any handle, so it does not need a fixed one.
        mockMvc.perform(patch("/api/creators/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"tiktokHandle\":\"" + handle + "\"}"))
                .andExpect(status().isOk());
        return new Creator(id, handle);
    }

    private String anyCreatorId() throws Exception {
        return anyCreator().id();
    }

    private long coverageRowCount() throws Exception {
        String response = mockMvc.perform(get("/api/coverage/log?size=200")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        return ((java.util.List<?>) com.jayway.jsonpath.JsonPath.read(response, "$.data")).size();
    }

    private int reportCalls() {
        return (int) STUB.requestedPaths().stream()
                .filter(path -> path.contains("/profile/") && path.endsWith("/report"))
                .count();
    }
}
