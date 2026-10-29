package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.insights.CreatorDbClient;
import com.generationb.support.CreatorDbStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreatorDB's answers mapped onto the app's own shapes, against a stub over a real socket.
 *
 * <p>The live API has no sandbox — every call there costs credits — so the response bodies here
 * are copied from the shapes in CreatorDB's OpenAPI document rather than recorded.
 */
class CreatorDbLookupTest {

    private CreatorDbStub stub;
    private CreatorDbClient client;
    private CreatorDbLookup lookup;

    @BeforeEach
    void start() throws IOException {
        stub = new CreatorDbStub();
        client = new CreatorDbClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", stub.baseUrl());
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(client, "minCredits", 100.0);

        lookup = new CreatorDbLookup(client);
        ReflectionTestUtils.setField(lookup, "postCacheMinutes", 60);
        ReflectionTestUtils.setField(lookup, "searchPageSize", 25);
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    private static Creator creator(String primary, String handle, String tiktok, String youtube) {
        Creator creator = new Creator();
        creator.setPrimaryPlatform(primary);
        creator.setHandle(handle);
        creator.setTiktokHandle(tiktok);
        creator.setYoutubeHandle(youtube);
        return creator;
    }

    // =====================================================================
    // Handles
    // =====================================================================

    @Test
    @DisplayName("handles are taken primary platform first, and Instagram only when it is primary")
    void targetsFollowThePrimaryPlatform() {
        assertEquals(List.of(
                        new CreatorDbLookup.Target("INSTAGRAM", "sophia"),
                        new CreatorDbLookup.Target("TIKTOK", "sophia.tt"),
                        new CreatorDbLookup.Target("YOUTUBE", "sophiayt")),
                CreatorDbLookup.targetsOf(creator("INSTAGRAM", "@sophia", "sophia.tt", "@sophiayt")));

        // The main handle of a TikTok creator is a TikTok handle; it must not be looked up on
        // Instagram, where it may belong to someone else.
        assertEquals(List.of(new CreatorDbLookup.Target("TIKTOK", "marcus")),
                CreatorDbLookup.targetsOf(creator("TIKTOK", "marcus", null, null)));

        assertEquals(List.of(), CreatorDbLookup.targetsOf(creator("INSTAGRAM", "not a handle", null, " ")),
                "invalid handles are never sent");
    }

    // =====================================================================
    // Profile refresh
    // =====================================================================

    @Test
    @DisplayName("a profile refresh reads followers, bio and the per-follower engagement rate")
    void snapshotMapsProfileAndPerformance() {
        stub.answer("/instagram/profile", "{\"uniqueId\":\"sophia\",\"bio\":\"Skincare\",\"totalFollowers\":45000}");
        stub.answer("/instagram/performance",
                "{\"imagesPerformanceRecent\":{\"engagement\":{\"avgEngagementRate\":0.0421}},"
                        + "\"reelsPerformanceRecent\":{\"engagement\":{\"avgEngagementRate\":0.09}}}");

        CreatorDbLookup.Snapshot snapshot =
                lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "sophia")).orElseThrow();

        assertEquals(45000L, snapshot.followers());
        assertEquals("Skincare", snapshot.bio());
        // The image rate, which is per follower: the reel rate is per view and is a different figure.
        assertEquals(new BigDecimal("4.21"), snapshot.erPercentage());
        assertTrue(stub.calls().stream().allMatch(c -> "test-key".equals(c.apiKey())),
                "the key travels in the api-key header");
        assertTrue(stub.bodies("/instagram/profile").getFirst().contains("\"uniqueId\":\"sophia\""));
    }

    @Test
    @DisplayName("an Instagram account with only reels gets the per-follower figure from them")
    void reelsOnlyEngagementIsPerFollower() {
        stub.answer("/instagram/profile", "{\"totalFollowers\":1000}");
        stub.answer("/instagram/performance",
                "{\"reelsPerformanceRecent\":{\"engagement\":{\"avgEngagementRate\":0.5,"
                        + "\"likesPerFollower\":0.03,\"commentsPerFollower\":0.002}}}");

        assertEquals(new BigDecimal("3.20"),
                lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "reelsonly")).orElseThrow().erPercentage());
    }

    @Test
    @DisplayName("YouTube reads subscribers and is looked up by channel id when that is what is stored")
    void youtubeUsesChannelIdWhenGiven() {
        stub.answer("/youtube/profile", "{\"totalSubscribers\":1200}");
        stub.answer("/youtube/performance",
                "{\"videosPerformanceRecent\":{\"engagement\":{\"avgEngagementRate\":0.0231}}}");

        CreatorDbLookup.Snapshot snapshot =
                lookup.snapshot(new CreatorDbLookup.Target("YOUTUBE", "UCabcdefghijklmnopqrstuv")).orElseThrow();

        assertEquals(1200L, snapshot.followers());
        assertEquals(new BigDecimal("2.31"), snapshot.erPercentage());
        assertTrue(stub.bodies("/youtube/profile").getFirst().contains("\"channelId\":\"UCabcdefghijklmnopqrstuv\""));
    }

    @Test
    @DisplayName("a creator CreatorDB has not indexed is reported as such, and costs nothing")
    void notFoundIsReportedAsNotIndexed() {
        CreatorDbLookup.SnapshotResult result = lookup.lookupSnapshot(new CreatorDbLookup.Target("INSTAGRAM", "nobody"));

        assertTrue(result.snapshot().isEmpty());
        assertTrue(result.notIndexed());
        assertEquals(0, stub.count("/instagram/performance"), "no second call for a creator that is not there");
        assertTrue(lookup.audience(new CreatorDbLookup.Target("INSTAGRAM", "nobody")).isEmpty());
    }

    @Test
    @DisplayName("a failure is not mistaken for a creator who is not indexed")
    void serverErrorIsNotNotIndexed() {
        stub.fail("/tiktok/profile", 500);
        assertFalse(lookup.lookupSnapshot(new CreatorDbLookup.Target("TIKTOK", "someone")).notIndexed());
    }

    // =====================================================================
    // Audience
    // =====================================================================

    private static final String AUDIENCE =
            "{\"audienceLocations\":[{\"country\":\"USA\",\"share\":0.5},{\"country\":\"GBR\",\"share\":0.3125},"
                    + "{\"country\":\"CAN\",\"share\":0.05}],"
                    + "\"audienceGender\":{\"maleRatio\":0.2,\"femaleRatio\":0.8},\"audienceAvgAge\":24,"
                    + "\"audienceAgeBreakdown\":[{\"ageRange\":\"13-17\",\"share\":0.1},{\"ageRange\":\"18-24\",\"share\":0.45},"
                    + "{\"ageRange\":\"25-34\",\"share\":0.38}]}";

    @Test
    @DisplayName("audience shares become the percentages and labels the profile shows")
    void audienceMapsToTheStoredShape() {
        stub.answer("/tiktok/audience", AUDIENCE);

        Map<String, Object> audience = lookup.audience(new CreatorDbLookup.Target("TIKTOK", "sophia.tt"));

        assertEquals(31.25, audience.get("ukAudiencePct"));
        assertEquals("US (50%)", audience.get("topLocation"));
        assertEquals("18-24 (45%)", audience.get("topAgeBand"));
        assertEquals("Female 80.0%, Male 20.0%", audience.get("genderSplit"));
        assertEquals(24, audience.get("averageAge"));
        assertEquals("CREATORDB", audience.get("source"));
    }

    @Test
    @DisplayName("the full breakdown keeps every country, largest first, and the age bands in order")
    void breakdownKeepsEverything() throws Exception {
        CreatorDbLookup.AudienceBreakdown breakdown =
                CreatorDbLookup.breakdown(new ObjectMapper().readTree(AUDIENCE));

        assertEquals(List.of("US", "GB", "CA"), breakdown.countries().stream().map(CreatorDbLookup.Share::key).toList());
        assertEquals(List.of("13-17", "18-24", "25-34"), breakdown.ageBands().stream().map(CreatorDbLookup.Share::key).toList());
        assertEquals(new BigDecimal("80.0"), breakdown.femalePct());
        assertFalse(breakdown.isEmpty());
    }

    @Test
    @DisplayName("an audience CreatorDB could not model is empty, not zeros")
    void nullAudienceIsEmpty() throws Exception {
        CreatorDbLookup.AudienceBreakdown breakdown = CreatorDbLookup.breakdown(new ObjectMapper().readTree(
                "{\"audienceLocations\":null,\"audienceGender\":null,\"audienceAvgAge\":null,\"audienceAgeBreakdown\":null}"));
        assertTrue(breakdown.isEmpty());
        assertTrue(CreatorDbLookup.summarise("INSTAGRAM", breakdown).isEmpty());
    }

    // =====================================================================
    // Posts
    // =====================================================================

    @Test
    @DisplayName("recent posts reach coverage with URLs, counts and no invented views")
    void postsMapToTheCoverageShape() {
        stub.answer("/instagram/content-detail",
                "{\"recentImages\":[{\"contentId\":\"DTQx\",\"publishTime\":1767901822000,\"description\":\"#ad\","
                        + "\"likes\":10,\"comments\":2}],"
                        + "\"recentReels\":[{\"contentId\":\"DS2p\",\"publishTime\":1767991822000,\"description\":\"reel\","
                        + "\"likes\":30,\"comments\":4,\"views\":900}]}");

        List<Map<String, Object>> posts = lookup.recentPosts(new CreatorDbLookup.Target("INSTAGRAM", "sophia"), 10);

        assertEquals(2, posts.size());
        Map<String, Object> reel = posts.get(0);
        assertEquals("https://www.instagram.com/reel/DS2p", reel.get("url"), "newest first");
        assertEquals("REEL", reel.get("postType"));
        assertEquals(900L, reel.get("views"));
        Map<String, Object> image = posts.get(1);
        assertEquals("https://www.instagram.com/p/DTQx", image.get("url"));
        assertFalse(image.containsKey("views"), "an image has no view count, and zero would be a lie");

        lookup.recentPosts(new CreatorDbLookup.Target("INSTAGRAM", "Sophia"), 10);
        assertEquals(1, stub.count("/instagram/content-detail"), "a second clip inside the cache window costs nothing");
    }

    @Test
    @DisplayName("TikTok and YouTube posts carry their own URLs, shares and long/short form")
    void tiktokAndYoutubePosts() {
        stub.answer("/tiktok/content-detail",
                "{\"recentVideos\":[{\"contentId\":\"7592006153727380766\",\"publishTime\":1767651698000,"
                        + "\"description\":\"fit check\",\"likes\":100,\"comments\":5,\"views\":5000,\"shares\":12}]}");
        stub.answer("/youtube/content-detail",
                "{\"recentVideos\":[{\"contentId\":\"QJI0an6irrA\",\"publishTime\":1767816009000,\"title\":\"Long one\","
                        + "\"likes\":9,\"comments\":1,\"views\":300}],"
                        + "\"recentShorts\":[{\"contentId\":\"IJkB-oapuks\",\"publishTime\":1766393469410,\"title\":\"Short\","
                        + "\"likes\":3,\"comments\":0,\"views\":80}]}");

        Map<String, Object> tiktok = lookup.recentPosts(new CreatorDbLookup.Target("TIKTOK", "sophia.tt"), 10).getFirst();
        assertEquals("https://www.tiktok.com/@sophia.tt/video/7592006153727380766", tiktok.get("url"));
        assertEquals(12L, tiktok.get("shares"));

        List<Map<String, Object>> youtube = lookup.recentPosts(new CreatorDbLookup.Target("YOUTUBE", "techdan"), 10);
        assertEquals("https://www.youtube.com/watch?v=QJI0an6irrA", youtube.get(0).get("url"));
        assertEquals("Long one", youtube.get(0).get("caption"), "YouTube's title, not its description");
        assertEquals("YOUTUBE_SHORT", youtube.get(1).get("postType"));
        assertEquals("https://www.youtube.com/shorts/IJkB-oapuks", youtube.get(1).get("url"));
    }

    // =====================================================================
    // Credits
    // =====================================================================

    @Test
    @DisplayName("once the balance reaches the reserve, no further calls are made")
    void stopsAtTheCreditReserve() {
        stub.fail("/instagram/profile", 402);
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "sophia"));
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "sophia"));

        assertEquals(1, stub.calls().size(), "the 402 reported a zero balance; the second call stays local");
        assertEquals(0.0, client.creditsAvailable().orElseThrow());
    }

    @Test
    @DisplayName("an unlimited quota, reported as -1, never trips the reserve")
    void unlimitedQuotaIsNotSpent() {
        stub.withCredits(-1).answer("/instagram/profile", "{\"totalFollowers\":1}");
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "a"));
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "b"));

        assertEquals(2, stub.count("/instagram/profile"));
    }

    @Test
    @DisplayName("a low balance reported on success also pauses calls")
    void lowBalanceOnSuccessPauses() {
        stub.withCredits(50).answer("/instagram/profile", "{\"totalFollowers\":1}");
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "a"));
        lookup.snapshot(new CreatorDbLookup.Target("INSTAGRAM", "b"));

        assertEquals(1, stub.count("/instagram/profile"));
    }

    // =====================================================================
    // Discovery
    // =====================================================================

    @Test
    @DisplayName("the legacy search filters on hashtags, sorts by size, and asks for no paid enrichment")
    void searchIsCheap() {
        stub.answer("/youtube/search",
                "{\"creatorList\":[{\"uniqueId\":\"techdan\",\"displayName\":\"Tech Dan\",\"totalSubscribers\":52000}],"
                        + "\"totalResults\":1,\"hasNextPage\":false}");

        List<Map<String, Object>> results = lookup.search("#unboxing, gadgets", "youtube", null);

        assertEquals(1, results.size());
        assertEquals("techdan", results.getFirst().get("handle"));
        assertEquals(52000L, results.getFirst().get("followers"));
        String body = stub.bodies("/youtube/search").getFirst();
        assertTrue(body.contains("\"filterName\":\"hashtags\""), body);
        assertTrue(body.contains("[\"unboxing\",\"gadgets\"]"), body);
        assertTrue(body.contains("\"sortBy\":\"totalSubscribers\""), body);
        assertFalse(body.contains("\"fields\""), "enrichment is charged per result");
    }

    @Test
    @DisplayName("discovery sends country as alpha-3 and the follower range on the platform's own field")
    void discoveryFilters() {
        stub.answer("/instagram/search",
                "{\"creatorList\":[{\"uniqueId\":\"glowgirl\",\"displayName\":\"Glow\",\"totalFollowers\":48000,"
                        + "\"avatarUrl\":\"https://cdn/x.jpg\"}],\"totalResults\":240,\"hasNextPage\":true,\"nextOffset\":25}");

        CreatorDbLookup.DiscoveryPage page = lookup.discover(new CreatorDbLookup.DiscoveryQuery(
                "instagram", "skincare #SPF", "gb", 10_000L, 100_000L, 0));

        assertEquals(1, page.creators().size());
        assertEquals(new CreatorDbLookup.DiscoveredCreator("INSTAGRAM", "glowgirl", "Glow", 48000L,
                "https://cdn/x.jpg", null), page.creators().getFirst());
        assertEquals(240, page.totalResults());
        assertTrue(page.hasNextPage());
        assertEquals(25, page.nextOffset());

        String body = stub.bodies("/instagram/search").getFirst();
        assertTrue(body.contains("[\"skincare\",\"spf\"]"), body);
        assertTrue(body.contains("{\"filterName\":\"country\",\"op\":\"=\",\"value\":\"GBR\"}")
                || body.contains("\"value\":\"GBR\""), body);
        assertTrue(body.contains("\"filterName\":\"totalFollowers\",\"op\":\">\",\"value\":10000")
                || (body.contains("\"op\":\">\"") && body.contains("10000")), body);
        assertTrue(body.contains("100000"), body);
    }

    @Test
    @DisplayName("YouTube handles, which CreatorDB returns with an @, come back bare")
    void youtubeHandlesAreBare() {
        // Seen against the live API: {"uniqueId":"@tiana07", ...}.
        stub.answer("/youtube/search",
                "{\"creatorList\":[{\"uniqueId\":\"@tiana07\",\"displayName\":\"Tiana\",\"totalSubscribers\":11300000,"
                        + "\"channelId\":\"UC6sSkkemzPjmrzS0Y0V_2zw\"}],\"totalResults\":1,\"hasNextPage\":false}");

        CreatorDbLookup.DiscoveredCreator found = lookup.discover(
                new CreatorDbLookup.DiscoveryQuery("YOUTUBE", null, null, null, null, 0)).creators().getFirst();

        assertEquals("tiana07", found.handle());
        assertEquals("UC6sSkkemzPjmrzS0Y0V_2zw", found.channelId());
    }

    @Test
    @DisplayName("an empty discovery query still searches, sorted by size, with no filters")
    void discoveryWithoutFilters() {
        stub.answer("/tiktok/search", "{\"creatorList\":[],\"totalResults\":0,\"hasNextPage\":false}");
        CreatorDbLookup.DiscoveryPage page = lookup.discover(
                new CreatorDbLookup.DiscoveryQuery("TIKTOK", " ", null, null, null, 0));

        assertTrue(page.creators().isEmpty());
        assertTrue(stub.bodies("/tiktok/search").getFirst().contains("\"filters\":[]"));
    }

    @Test
    @DisplayName("an unknown platform is never sent to CreatorDB")
    void discoveryRejectsUnknownPlatforms() {
        assertTrue(lookup.discover(new CreatorDbLookup.DiscoveryQuery("LINKEDIN", "x", null, null, null, 0))
                .creators().isEmpty());
        assertTrue(stub.calls().isEmpty());
    }

    @Test
    @DisplayName("keywords and countries are normalised before they cost a credit")
    void normalisation() {
        assertEquals(List.of("skincare", "spf"), CreatorDbLookup.keywordsOf("#Skincare, skincare;  SPF"));
        assertEquals(List.of(), CreatorDbLookup.keywordsOf("  "));
        assertEquals("GBR", CreatorDbLookup.alpha3("gb"));
        assertEquals("GBR", CreatorDbLookup.alpha3("GBR"));
        assertNull(CreatorDbLookup.alpha3("XX"));
        assertNull(CreatorDbLookup.alpha3("England"));
        assertEquals("GB", CreatorDbLookup.country("GBR"));
    }

    // =====================================================================
    // Submitting
    // =====================================================================

    @Test
    @DisplayName("a creator is submitted by handle, and a YouTube channel id as a channel id")
    void submitUsesTheRightIdentifier() {
        stub.answer("/instagram/submitCreators", "{\"results\":[{\"uniqueId\":\"newbie\",\"status\":\"accepted\"}]}");
        stub.answer("/youtube/submitCreators", "{\"results\":[{\"channelId\":\"UCabcdefghijklmnopqrstuv\",\"status\":\"done\"}]}");

        assertEquals("accepted", lookup.submit(new CreatorDbLookup.Target("INSTAGRAM", "newbie")).orElseThrow());
        assertEquals("done", lookup.submit(new CreatorDbLookup.Target("YOUTUBE", "UCabcdefghijklmnopqrstuv")).orElseThrow());

        assertEquals("{\"uniqueIds\":[\"newbie\"]}", stub.bodies("/instagram/submitCreators").getFirst());
        assertEquals("{\"channelIds\":[\"UCabcdefghijklmnopqrstuv\"]}", stub.bodies("/youtube/submitCreators").getFirst());
    }

    @Test
    @DisplayName("a failed submission is empty rather than a guessed status")
    void failedSubmissionIsEmpty() {
        stub.fail("/tiktok/submitCreators", 500);
        assertTrue(lookup.submit(new CreatorDbLookup.Target("TIKTOK", "x")).isEmpty());
    }
}
