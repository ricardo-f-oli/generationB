package com.generationb.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for the Modash API, served over real HTTP on localhost.
 *
 * <p>Deliberately a real socket rather than a mocked {@code ModashClient}. Everything that has
 * actually gone wrong with this integration lives <em>below</em> the client's public methods:
 * the bearer header, the retry on 429, honouring {@code Retry-After}, the credit guard reading
 * {@code /user/info}, and the fact that Modash answers {@code 200 OK} with
 * {@code {"error": true}} in the body. A mock that returns a {@code JsonNode} skips all of it.
 *
 * <p>The payloads are trimmed captures of real responses, so the field names are the vendor's
 * rather than what the documentation implies — which is how the {@code engagementRate}
 * fraction-versus-percentage bug was caught in the first place.
 *
 * <p>No test hits the live API. It is metered, and a CI run that spends the agency's credits is
 * a bill, not a test.
 */
public class ModashStub implements AutoCloseable {

    private final HttpServer server;

    /** Every path requested, in order, so a test can assert what was and was not called. */
    private final List<String> requests = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();

    /** Per-path queued responses. A path with none falls back to {@link #defaultFor}. */
    private final Map<String, List<Response>> queued = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    private volatile double credits = 100;
    private volatile int rawRequests = 100;

    /**
     * Bumped on every {@link #reset()} so each test sees posts it has not clipped before.
     * Coverage dedupes on URL, and these tests share one database, so a fixed feed would make
     * every test after the first find nothing but duplicates.
     */
    private static final AtomicInteger FEED_NONCE = new AtomicInteger();
    private volatile String nonce = "A";

    public record Response(int status, String body, Map<String, String> headers) {
        public static Response ok(String body) {
            return new Response(200, body, Map.of());
        }

        public static Response status(int status) {
            return new Response(status, "{}", Map.of());
        }

        /** A 429 that tells the caller exactly how long to wait. */
        public static Response rateLimited(int retryAfterSeconds) {
            return new Response(429, "{\"error\":true,\"message\":\"rate limited\"}",
                    Map.of("Retry-After", String.valueOf(retryAfterSeconds)));
        }
    }

    public ModashStub() throws IOException {
        // Port 0: the OS picks a free one, so parallel test classes cannot collide.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    // =====================================================================
    // Scripting
    // =====================================================================

    /** Queues one response for the next request to {@code path}. Calls are consumed in order. */
    public ModashStub enqueue(String path, Response response) {
        queued.computeIfAbsent(path, key -> new ArrayList<>()).add(response);
        return this;
    }

    public ModashStub setBalance(double credits, int rawRequests) {
        this.credits = credits;
        this.rawRequests = rawRequests;
        return this;
    }

    public List<String> requestedPaths() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public int hitCount(String path) {
        return hits.getOrDefault(path, new AtomicInteger()).get();
    }

    public List<String> authorizationHeaders() {
        synchronized (authHeaders) {
            return List.copyOf(authHeaders);
        }
    }

    public void reset() {
        nonce = "N" + FEED_NONCE.incrementAndGet();
        synchronized (requests) {
            requests.clear();
        }
        synchronized (authHeaders) {
            authHeaders.clear();
        }
        queued.clear();
        hits.clear();
        credits = 100;
        rawRequests = 100;
    }

    // =====================================================================
    // Serving
    // =====================================================================

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        synchronized (requests) {
            requests.add(path);
        }
        synchronized (authHeaders) {
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        }
        hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();

        Response response = next(path);
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);

        response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private Response next(String path) {
        List<Response> scripted = queued.get(path);
        if (scripted != null && !scripted.isEmpty()) {
            return scripted.remove(0);
        }
        return defaultFor(path);
    }

    private Response defaultFor(String path) {
        if (path.equals("/user/info")) {
            return Response.ok("""
                {"user":{"name":"","email":""},
                 "billing":{"credits":%s,"rawRequests":%d},
                 "rateLimits":{"discoveryRatelimit":2,"rawRatelimit":2},
                 "error":false}
                """.formatted(credits, rawRequests));
        }
        if (path.startsWith("/raw/ig/")) {
            return Response.ok(igFeed(nonce).replace(" #CAMPAIGN_TAG", ""));
        }
        if (path.startsWith("/raw/tiktok/")) {
            return Response.ok(tiktokFeed(nonce));
        }
        if (path.contains("/profile/") && path.endsWith("/report")) {
            return Response.ok(REPORT);
        }
        if (path.startsWith("/ai/")) {
            return Response.ok(AI_SEARCH);
        }
        if (path.endsWith("/users")) {
            return Response.ok(USER_LOOKUP);
        }
        return Response.ok("{\"error\":false}");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // =====================================================================
    // Captured payloads
    // =====================================================================

    /**
     * A real Instagram feed. Note every {@code view_count} and {@code play_count} is null —
     * Instagram publishes no view count on any post type, which is the whole reason
     * {@code coverage_items.views} is nullable.
     */
    /** The captured feed with per-run post codes, so URLs are unique across tests. */
    /**
     * The feed with a campaign tracking tag substituted into the first caption.
     *
     * <p>For attribution tests: a post carrying the campaign's tag is a briefed creator
     * delivering, and the second post — which carries no tag — must NOT be attributed, because
     * auto-clipping fetches a creator's posts wholesale and most are about something else.
     */
    public static String igFeed(String nonce, String campaignTag) {
        return igFeed(nonce).replace("#CAMPAIGN_TAG", "#" + campaignTag);
    }

    public static String igFeed(String nonce) {
        return IG_FEED.replace("DcZ1WbThFC5", "DcZ1WbThFC5" + nonce)
                .replace("DcZ1WbThFC6", "DcZ1WbThFC6" + nonce)
                .replace("3970439184115847353", "397043918411584735" + nonce)
                .replace("3970439184115847354", "397043918411584736" + nonce);
    }

    public static String tiktokFeed(String nonce) {
        return TIKTOK_FEED.replace("7300000000000000001", "730000000000000000" + nonce);
    }

    public static final String IG_FEED = """
        {"items":[
          {"pk":"3970439184115847353","code":"DcZ1WbThFC5","product_type":"clips","media_type":2,
           "taken_at":1787533205,"like_count":77,"comment_count":32,
           "view_count":null,"play_count":null,"is_paid_partnership":true,
           "caption":{"text":"Loving this mask #ad #CAMPAIGN_TAG"},
           "user":{"pk":"12016731292","username":"stubcreator"}},
          {"pk":"3970439184115847354","code":"DcZ1WbThFC6","product_type":"carousel_container",
           "media_type":8,"taken_at":1787433205,"like_count":53,"comment_count":16,
           "caption":{"text":"Swipe"},"user":{"username":"stubcreator"}}
        ],"more_available":true,"end_cursor":"igc1.abc"}
        """;

    /** TikTok does publish play counts, so these rows carry real views. */
    public static final String TIKTOK_FEED = """
        {"user_feed":{"items":[
          {"id":"7300000000000000001","desc":"unboxing #gifted","createTime":1787433205,
           "author":{"id":"1","uniqueId":"stubcreator","nickname":"Stub"},
           "stats":{"playCount":28900,"diggCount":3100,"commentCount":142,"shareCount":40,
                    "collectCount":12},
           "authorStats":{"followerCount":54000}}
        ]},"success":true,"hasMore":false,"maxCursor":"0"}
        """;

    /** engagementRate here is a FRACTION. On /ai/ it is a percentage. */
    public static final String REPORT = """
        {"error":false,"profile":{
          "userId":"12016731292",
          "profile":{"fullname":"Stub Creator","username":"stubcreator",
                     "followers":110848,"engagementRate":0.0421},
          "audience":{
            "credibility":0.9012,
            "genders":[{"code":"MALE","weight":0.09},{"code":"FEMALE","weight":0.91}],
            "ages":[{"code":"18-24","weight":0.39},{"code":"25-34","weight":0.44}],
            "geoCountries":[{"name":"United Kingdom","code":"GB","weight":0.643},
                            {"name":"United States","code":"US","weight":0.21}]},
          "country":"GB",
          "interests":[{"id":80,"name":"Beauty & Cosmetics"}],
          "contacts":[{"type":"email","value":"stub@example.com"}]}}
        """;

    public static final String AI_SEARCH = """
        {"error":false,"total":240,"profiles":[
          {"userId":"39442151361","fullName":"Sandra Booth-Martin","username":"mrs_sandra_bm",
           "followersCount":6310,"engagementRate":1.47,"viewsCountMedian":355,
           "profilePicture":"https://images.od.modash.io/abc"}
        ]}
        """;

    public static final String USER_LOOKUP = """
        {"error":false,"users":[
          {"username":"stubcreator","fullname":"Stub Creator","followers":110830,
           "userId":"12016731292","isVerified":true,"picture":"https://img/x"}
        ]}
        """;
}
