package com.generationb.creators.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.insights.ModashClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The translation from Modash's payloads into ours.
 *
 * <p>This is the layer worth testing hardest. A mapping mistake here does not throw — it puts a
 * plausible wrong number in front of a client. Two in particular have bitten before: reading an
 * engagement rate expressed as a fraction as though it were a percentage, and turning "the
 * platform does not publish this" into a zero.
 *
 * <p>The fixtures are trimmed captures of real responses from the live API, so the field names
 * and the shapes are the vendor's rather than what the docs imply.
 */
class ModashMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns canned JSON instead of calling the vendor.
     *
     * <p>The repository is null throughout: none of the three paths exercised here
     * ({@code fetchReport}, {@code getMentions}, {@code searchCreators}) resolves a creator by id.
     */
    private static ModashCreatorInsightsProvider providerReturning(String json) {
        ModashClient stub = new ModashClient(MAPPER) {
            @Override
            public Optional<JsonNode> get(String path, Map<String, String> query, Meter meter, double cost) {
                return parse(json);
            }

            @Override
            public Optional<JsonNode> post(String path, Object body, Meter meter, double cost) {
                return parse(json);
            }
        };
        return new ModashCreatorInsightsProvider(stub, null);
    }

    private static Optional<JsonNode> parse(String json) {
        try {
            return Optional.of(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // =====================================================================
    // Audience report (#26 / #55)
    // =====================================================================

    private static final String REPORT = """
        {"error": false, "profile": {
          "userId": "12016731292",
          "profile": {"fullname": "MEDIHEAL USA", "username": "mediheal_usa",
                      "followers": 110848, "engagementRate": 0.0011117895254845853},
          "audience": {
            "credibility": 0.8134,
            "genders": [{"code": "MALE", "weight": 0.091435},
                        {"code": "FEMALE", "weight": 0.908565}],
            "ages": [{"code": "13-17", "weight": 0.070109},
                     {"code": "18-24", "weight": 0.393079},
                     {"code": "25-34", "weight": 0.41109}],
            "geoCountries": [{"name": "United States", "code": "US", "weight": 0.816242},
                             {"name": "United Kingdom", "code": "GB", "weight": 0.121},
                             {"name": "Canada", "code": "CA", "weight": 0.014226}]
          },
          "country": "KR",
          "interests": [{"id": 7, "name": "Shopping & Retail"}, {"id": 80, "name": "Beauty"}],
          "contacts": [{"type": "email", "value": "hello@example.com"}]
        }}
        """;

    @Test
    void engagementRateIsConvertedFromFractionToPercentage() {
        Map<String, Object> report = providerReturning(REPORT)
                .fetchReport("mediheal_usa", "instagram").orElseThrow();

        // 0.0011117... is 0.11%, not 0.0011%. Reading it raw would put a rate three orders of
        // magnitude too small into the KPI comparison, and every creator would fail every target.
        //
        // Two decimals: at 1dp this rounds to 0.1% and a KPI target of "at least 0.15%" would be
        // compared against a number that had already lost the digit deciding it.
        assertEquals(0.11, (Double) report.get("engagementRatePct"), 0.001);
    }

    @Test
    void ukAudienceComesFromTheGbEntryNotTheTopCountry() {
        Map<String, Object> report = providerReturning(REPORT)
                .fetchReport("mediheal_usa", "instagram").orElseThrow();

        // The heaviest country here is the US. Requirement #26 asks specifically for the UK share,
        // so picking the first entry would report 81.6% UK for a US-heavy audience.
        assertEquals(12.1, (Double) report.get("ukAudiencePct"), 0.001);
        assertEquals("United States (81.6%)", report.get("topLocation"));
    }

    @Test
    void heaviestAgeBandWinsRatherThanTheFirstListed() {
        Map<String, Object> report = providerReturning(REPORT)
                .fetchReport("mediheal_usa", "instagram").orElseThrow();

        // 25-34 outweighs 18-24; 13-17 is listed first and is the smallest.
        assertEquals("25-34 (41.1%)", report.get("topAgeBand"));
    }

    @Test
    void genderSplitAndCredibilityAreReadableAndNumeric() {
        Map<String, Object> report = providerReturning(REPORT)
                .fetchReport("mediheal_usa", "instagram").orElseThrow();

        assertEquals("Female 90.9%, Male 9.1%", report.get("genderSplit"));
        assertEquals("81.3%", report.get("authenticityScore"));
        assertEquals(81.3, (Double) report.get("credibilityPct"), 0.001);
        assertEquals(110848, report.get("followers"));
        assertEquals("Shopping & Retail", report.get("niche"));
        assertEquals("hello@example.com", report.get("email"));
    }

    @Test
    void anEmptyProfileIsNoAnswerRatherThanBlankFigures() {
        // A private or unknown account. Returning an empty map here would let the enrichment
        // service write nulls over demographics somebody researched by hand.
        assertTrue(providerReturning("{\"error\": false, \"profile\": {}}")
                .fetchReport("nobody", "instagram").isEmpty());
    }

    // =====================================================================
    // Post feeds (#10 / #11)
    // =====================================================================

    private static final String IG_FEED = """
        {"items": [
          {"pk": "3970439184115847353", "code": "DcZ1WbThFC5", "product_type": "clips",
           "media_type": 2, "taken_at": 1787533205, "like_count": 77, "comment_count": 32,
           "view_count": null, "play_count": null, "is_paid_partnership": true,
           "caption": {"text": "Loving this mask #ad"},
           "user": {"pk": "12016731292", "username": "mediheal_usa"}},
          {"pk": "3970439184115847354", "code": "DcZ1WbThFC6", "product_type": "carousel_container",
           "media_type": 8, "taken_at": 1787433205, "like_count": 53, "comment_count": 16,
           "caption": {"text": "Swipe"}, "user": {"username": "mediheal_usa"}}
        ], "more_available": true, "end_cursor": "igc1.abc"}
        """;

    @Test
    void instagramPostsCarryNoViewCountAtAll() {
        List<Map<String, Object>> posts = providerReturning(IG_FEED).getMentions("mediheal", 10);

        // Verified against the live API on both a brand account and a creator account: Instagram
        // publishes no view count on any post type. The key must be absent, because
        // CoverageService reads an absent key as "not tracked" and a present 0 as "measured zero".
        assertFalse(posts.isEmpty());
        assertTrue(posts.stream().noneMatch(post -> post.containsKey("views")),
                "an Instagram post must not claim a view count");
    }

    @Test
    void instagramPostTypeAndUrlFollowTheProductType() {
        List<Map<String, Object>> posts = providerReturning(IG_FEED).getMentions("mediheal", 10);

        // A reel lives at /reel/, not /p/ — the dedupe key is the URL, so getting this wrong
        // would log every reel twice.
        assertEquals("REEL", posts.get(0).get("postType"));
        assertEquals("https://www.instagram.com/reel/DcZ1WbThFC5/", posts.get(0).get("url"));
        assertEquals("CAROUSEL", posts.get(1).get("postType"));
        assertEquals("https://www.instagram.com/p/DcZ1WbThFC6/", posts.get(1).get("url"));
    }

    @Test
    void mentionsAreTaggedWithTheTermThatFoundThem() {
        List<Map<String, Object>> posts = providerReturning(IG_FEED).getMentions("#katieloxton", 10);

        assertEquals("#katieloxton", posts.get(0).get("mention"));
        assertEquals(77L, posts.get(0).get("likes"));
        assertEquals(32L, posts.get(0).get("comments"));
        assertEquals(Boolean.TRUE, posts.get(0).get("paidPartnership"));
    }

    @Test
    void aMentionSweepIsCappedAtTheRequestedLimit() {
        // Each term is a metered request and each item is a row in the client's coverage log.
        assertEquals(1, providerReturning(IG_FEED).getMentions("mediheal", 1).size());
    }

    @Test
    void aBlankSearchTermCostsNothing() {
        assertTrue(providerReturning(IG_FEED).getMentions("   ", 10).isEmpty());
    }

    // =====================================================================
    // Natural-language search (#23)
    // =====================================================================

    @Test
    void aiSearchEngagementRateIsAlreadyAPercentageAndIsNotConvertedAgain() {
        // Captured from the live endpoint. This account has 6,310 followers and averages roughly
        // 100 likes a post, so 1.47 is the percentage itself. The report endpoint expresses the
        // same measure as a fraction; treating them alike turned 1.5% into 147% on screen.
        String response = """
            {"error": false, "total": 240, "profiles": [
              {"userId": "39442151361", "fullName": "Sandra Booth-Martin",
               "username": "mrs_sandra_bm", "followersCount": 6310, "engagementRate": 1.47,
               "viewsCountMedian": 355, "profilePicture": "https://images.od.modash.io/abc"}
            ]}
            """;
        List<Map<String, Object>> found = providerReturning(response)
                .searchCreators("UK skincare reviewers", "instagram", null);

        assertEquals(1, found.size());
        assertEquals("mrs_sandra_bm", found.get(0).get("handle"));
        assertEquals("Sandra Booth-Martin", found.get(0).get("name"));
        assertEquals(6310, found.get(0).get("followers"));
        assertEquals(1.5, (Double) found.get(0).get("er"), 0.001);
        assertEquals(355L, found.get(0).get("medianViews"));
        assertEquals("39442151361", found.get(0).get("externalId"));
    }

    @Test
    void aProfileWithoutAUsernameIsDropped() {
        // Nothing downstream can act on a result we cannot address. Keeping it would put a blank
        // row on the discovery screen that silently fails when someone clicks it.
        String response = """
            {"error": false, "profiles": [{"followersCount": 100},
                                          {"username": "real", "followersCount": 5}]}
            """;
        List<Map<String, Object>> found =
                providerReturning(response).searchCreators("anything", "instagram", null);

        assertEquals(1, found.size());
        assertEquals("real", found.get(0).get("handle"));
    }

    @Test
    void theFreeHandleLookupUsesItsOwnFieldNames() {
        // /instagram/users answers with fullname/followers/picture, where AI search answers with
        // fullName/followersCount/profilePicture. One mapper for both silently zeroed every
        // follower count on one of the two screens.
        String response = """
            {"error": false, "users": [
              {"username": "mediheal_usa", "fullname": "MEDIHEAL USA", "followers": 110830,
               "userId": "12016731292", "isVerified": true, "picture": "https://img/x"}
            ]}
            """;
        List<Map<String, Object>> found =
                providerReturning(response).lookupHandles("mediheal", "instagram", 5);

        assertEquals(1, found.size());
        assertEquals("MEDIHEAL USA", found.get(0).get("name"));
        assertEquals(110830, found.get(0).get("followers"));
        assertEquals(Boolean.TRUE, found.get(0).get("verified"));
        // The lookup carries no engagement rate, and inventing one would be worse than omitting it.
        assertFalse(found.get(0).containsKey("er"));
    }
}
