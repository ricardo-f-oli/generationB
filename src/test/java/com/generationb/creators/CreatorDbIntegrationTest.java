package com.generationb.creators;

import com.generationb.support.CreatorDbStub;
import com.generationb.support.IntegrationTest;
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
 * CreatorDB end to end: HTTP request → controller → service → lookup → client → a stubbed
 * CreatorDB over a real socket → PostgreSQL.
 *
 * <p>What only this level proves: that the key alone selects CreatorDB over the mock, that a
 * refresh writes what the profile reads, that a bought audience report is stored as JSONB and
 * read back for free, that discovery marks creators already on the database, and that clipping
 * reaches the coverage table.
 */
@TestPropertySource(properties = {
        "insights.creatordb.api-key=integration-test-creatordb-key",
        "insights.creatordb.min-credits=0",
})
class CreatorDbIntegrationTest extends IntegrationTest {

    private static final CreatorDbStub STUB;

    static {
        try {
            STUB = new CreatorDbStub();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void creatorDbProperties(DynamicPropertyRegistry registry) {
        registry.add("insights.creatordb.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    private static final String AUDIENCE =
            "{\"audienceLocations\":[{\"country\":\"GBR\",\"share\":0.6},{\"country\":\"IRL\",\"share\":0.1}],"
                    + "\"audienceGender\":{\"maleRatio\":0.3,\"femaleRatio\":0.7},\"audienceAvgAge\":26,"
                    + "\"audienceAgeBreakdown\":[{\"ageRange\":\"18-24\",\"share\":0.5},{\"ageRange\":\"25-34\",\"share\":0.3}]}";

    private String adminToken;

    @BeforeEach
    void signIn() throws Exception {
        STUB.reset();
        adminToken = auth.tokenFor(mockMvc, TestAuth.ADMIN);
    }

    @Test
    @DisplayName("the key alone makes CreatorDB a live source")
    void creatorDbIsLive() throws Exception {
        mockMvc.perform(get("/api/creators/profile-refresh/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.live").value(true))
                .andExpect(jsonPath("$.data.creatorDb").value(true));
        mockMvc.perform(get("/api/creators/discovery/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("a TikTok creator is refreshed from CreatorDB, demographics and report included")
    void tiktokRefresh() throws Exception {
        String handle = unique("tt");
        String id = createCreator(handle, "TIKTOK");
        STUB.answer("/tiktok/profile", "{\"uniqueId\":\"" + handle + "\",\"bio\":\"Fits\",\"totalFollowers\":81000}");
        STUB.answer("/tiktok/performance", "{\"videosPerformanceRecent\":{\"engagement\":{\"avgEngagementRate\":0.064}}}");
        STUB.answer("/tiktok/audience", AUDIENCE);

        mockMvc.perform(post("/api/creators/" + id + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshed").value(true));

        mockMvc.perform(get("/api/creators/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.followersCount").value(81000))
                .andExpect(jsonPath("$.data.erPercentage").value(6.4))
                .andExpect(jsonPath("$.data.ukAudiencePct").value(60.0))
                .andExpect(jsonPath("$.data.audienceAgeBand").value("18-24 (50%)"))
                .andExpect(jsonPath("$.data.insightsSource").value("CREATORDB"));

        // The audience bought by the refresh is the stored report: reading it costs nothing.
        long before = STUB.calls().size();
        mockMvc.perform(get("/api/creators/" + id + "/audience-report")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.reports", hasSize(1)))
                .andExpect(jsonPath("$.data.reports[0].platform").value("TIKTOK"))
                .andExpect(jsonPath("$.data.reports[0].countries[1].key").value("IE"))
                .andExpect(jsonPath("$.data.reports[0].averageAge").value(26));
        assertEquals(before, STUB.calls().size());
        assertTrue(STUB.calls().stream().allMatch(c -> "integration-test-creatordb-key".equals(c.apiKey())));
    }

    @Test
    @DisplayName("buying an audience report stores it, and buying again replaces it")
    void audienceReportPurchase() throws Exception {
        String handle = unique("ig");
        String id = createCreator(handle, "INSTAGRAM");
        STUB.answer("/instagram/audience", AUDIENCE);

        mockMvc.perform(post("/api/creators/" + id + "/audience-report")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.countries[0].key").value("GB"))
                .andExpect(jsonPath("$.data.femalePct").value(70.0));
        mockMvc.perform(post("/api/creators/" + id + "/audience-report?platform=INSTAGRAM")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM creator_audience_reports WHERE creator_id = ?::uuid", Integer.class, id);
        assertEquals(1, rows);
        mockMvc.perform(get("/api/creators/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.ukAudiencePct").value(60.0));
    }

    @Test
    @DisplayName("a creator CreatorDB does not know is submitted, and the reason says so")
    void unknownCreatorIsSubmitted() throws Exception {
        String handle = unique("new");
        String id = createCreator(handle, "INSTAGRAM");
        STUB.answer("/instagram/submitCreators", "{\"results\":[{\"uniqueId\":\"" + handle + "\",\"status\":\"accepted\"}]}");

        mockMvc.perform(post("/api/creators/" + id + "/audience-report")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message", containsString("submitted for indexing")));

        mockMvc.perform(post("/api/creators/" + id + "/enrich?force=true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.refreshed").value(false))
                .andExpect(jsonPath("$.data.reason", containsString("has not indexed")));

        assertEquals(2, STUB.count("/instagram/submitCreators"));
        assertTrue(STUB.bodies("/instagram/submitCreators").getFirst().contains(handle));
    }

    @Test
    @DisplayName("discovery returns CreatorDB's page and marks creators already on the database")
    void discovery() throws Exception {
        String known = unique("known");
        String knownId = createCreator(known, "INSTAGRAM");
        STUB.answer("/instagram/search", "{\"creatorList\":["
                + "{\"uniqueId\":\"" + known + "\",\"displayName\":\"Known\",\"totalFollowers\":50000},"
                + "{\"uniqueId\":\"brandnewglow\",\"displayName\":\"Glow\",\"totalFollowers\":40000}],"
                + "\"totalResults\":2,\"hasNextPage\":false}");

        mockMvc.perform(post("/api/creators/discovery/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"platform\":\"INSTAGRAM\",\"keywords\":\"skincare\",\"country\":\"GB\",\"minFollowers\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results", hasSize(2)))
                .andExpect(jsonPath("$.data.results[0].existingCreatorId").value(knownId))
                .andExpect(jsonPath("$.data.results[1].existingCreatorId").doesNotExist())
                .andExpect(jsonPath("$.data.results[1].profileUrl").value("https://www.instagram.com/brandnewglow"))
                .andExpect(jsonPath("$.data.creditsAvailable").value(9000.0));

        String body = STUB.bodies("/instagram/search").getFirst();
        assertTrue(body.contains("GBR"), body);
    }

    @Test
    @DisplayName("invalid discovery input is refused without a call")
    void discoveryValidation() throws Exception {
        mockMvc.perform(post("/api/creators/discovery/search")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"platform\":\"INSTAGRAM\",\"country\":\"England\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals(0, STUB.calls().size());
    }

    @Test
    @DisplayName("clipping a creator's posts reaches the coverage log")
    void clipping() throws Exception {
        String handle = unique("clip");
        String id = createCreator(handle, "INSTAGRAM");
        STUB.answer("/instagram/content-detail", "{\"recentReels\":[{\"contentId\":\"R" + handle
                + "\",\"publishTime\":1767991822000,\"description\":\"new drop\",\"likes\":30,\"comments\":4,\"views\":900}]}");

        mockMvc.perform(post("/api/coverage/clip")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"creatorId\":\"" + id + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.captured").value(1));
    }

    // =====================================================================

    private static String unique(String prefix) {
        return prefix + System.nanoTime() % 10_000_000;
    }

    private String createCreator(String handle, String platform) throws Exception {
        String extra = "TIKTOK".equals(platform) ? ",\"tiktokHandle\":\"" + handle + "\"" : "";
        String response = mockMvc.perform(post("/api/creators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"CDB Creator\",\"handle\":\"" + handle + "\",\"primaryPlatform\":\""
                                + platform + "\",\"followersCount\":10" + extra + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }
}
