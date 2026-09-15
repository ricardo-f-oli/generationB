package com.generationb.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for Meta's Graph API and the YouTube Data API, served over real HTTP on localhost.
 *
 * <p>A real socket rather than mocked clients, because what goes wrong with these integrations
 * lives below the clients' public methods: where the token travels, how the Business Discovery
 * field expansion is written, and how an error body is read. A mock returning a {@code JsonNode}
 * skips all of it.
 *
 * <p>Meta is served under {@code /v…/}, YouTube under {@code /youtube/v3/}, so one stub backs
 * both base URLs. No test reaches the real APIs.
 */
public class PlatformApiStub implements AutoCloseable {

    public static final String IG_USER_ID = "17841400000000001";

    private static final Pattern USERNAME = Pattern.compile("username\\(([^)]*)\\)");

    private final HttpServer server;

    /** Every request as "path?query", in order, so a test can assert what was called and how. */
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizationHeaders = new ArrayList<>();
    private final List<String> apiKeyHeaders = new ArrayList<>();

    /** Per-path-prefix queued failures. A path with none gets the default answer. */
    private final Map<String, List<Integer>> failures = new ConcurrentHashMap<>();

    /**
     * Bumped on every {@link #reset()} so each test sees posts it has not clipped before. Coverage
     * dedupes on URL, and these tests share one database.
     */
    private static final AtomicInteger NONCE = new AtomicInteger();
    private volatile String nonce = "A";
    private volatile String campaignTag = null;
    private volatile int followers = 110848;

    public PlatformApiStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String metaBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String youtubeBaseUrl() {
        return metaBaseUrl() + "/youtube/v3";
    }

    // =====================================================================
    // Scripting
    // =====================================================================

    /** The next request whose path contains {@code pathPart} answers with {@code status}. */
    public PlatformApiStub failNext(String pathPart, int status) {
        failures.computeIfAbsent(pathPart, key -> new ArrayList<>()).add(status);
        return this;
    }

    /** Puts {@code #tag} in the first Instagram post's caption, as a briefed creator would. */
    public PlatformApiStub withCampaignTag(String tag) {
        this.campaignTag = tag;
        return this;
    }

    public PlatformApiStub withFollowers(int followers) {
        this.followers = followers;
        return this;
    }

    public List<String> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public long countRequests(String pathPart) {
        return requests().stream().filter(request -> request.contains(pathPart)).count();
    }

    public List<String> authorizationHeaders() {
        synchronized (authorizationHeaders) {
            return List.copyOf(authorizationHeaders);
        }
    }

    public List<String> apiKeyHeaders() {
        synchronized (apiKeyHeaders) {
            return List.copyOf(apiKeyHeaders);
        }
    }

    public String nonce() {
        return nonce;
    }

    public void reset() {
        nonce = "N" + NONCE.incrementAndGet();
        campaignTag = null;
        followers = 110848;
        failures.clear();
        synchronized (requests) {
            requests.clear();
        }
        synchronized (authorizationHeaders) {
            authorizationHeaders.clear();
        }
        synchronized (apiKeyHeaders) {
            apiKeyHeaders.clear();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // =====================================================================
    // Serving
    // =====================================================================

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery() == null
                ? "" : URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
        synchronized (requests) {
            requests.add(path + "?" + query);
        }
        if (path.startsWith("/youtube/")) {
            synchronized (apiKeyHeaders) {
                apiKeyHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Goog-Api-Key")));
            }
        } else {
            synchronized (authorizationHeaders) {
                authorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            }
        }

        int status = 200;
        String body;
        Integer failure = nextFailure(path);
        if (failure != null) {
            status = failure;
            body = """
                {"error":{"message":"stubbed failure","type":"OAuthException","code":100}}
                """;
        } else {
            body = answer(path, query);
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Integer nextFailure(String path) {
        for (Map.Entry<String, List<Integer>> entry : failures.entrySet()) {
            if (path.contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().remove(0);
            }
        }
        return null;
    }

    private String answer(String path, String query) {
        if (path.startsWith("/youtube/v3/channels")) {
            return YT_CHANNEL.formatted(nonce);
        }
        if (path.startsWith("/youtube/v3/playlistItems")) {
            return YT_PLAYLIST.formatted(nonce);
        }
        if (path.startsWith("/youtube/v3/videos")) {
            return YT_VIDEOS.formatted(nonce);
        }
        if (path.endsWith("/" + IG_USER_ID)) {
            Matcher matcher = USERNAME.matcher(query);
            String username = matcher.find() ? matcher.group(1) : "unknown";
            return query.contains("media.limit")
                    ? igProfileWithMedia(username)
                    : IG_PROFILE.formatted(username, followers);
        }
        return "{\"data\":[]}";
    }

    private String igProfileWithMedia(String username) {
        String tagged = campaignTag == null ? "" : " #" + campaignTag;
        return """
            {"business_discovery":{"username":"%s","biography":"Skincare and slow mornings. London.",
              "followers_count":%d,"media":{"data":[
              {"id":"1790000000000%s1","caption":"Loving this mask #ad%s","like_count":77,
               "comments_count":32,"media_type":"VIDEO","media_product_type":"REELS",
               "permalink":"https://www.instagram.com/reel/DcZ1Wb%s/","timestamp":"2026-09-10T10:00:00+0000"},
              {"id":"1790000000000%s2","caption":"Swipe","like_count":53,"comments_count":16,
               "media_type":"CAROUSEL_ALBUM","media_product_type":"FEED",
               "permalink":"https://www.instagram.com/p/DcZ2Wb%s/","timestamp":"2026-09-09T10:00:00+0000"}
            ]}},"id":"%s"}
            """.formatted(username, followers, nonce, tagged, nonce, nonce, nonce, IG_USER_ID);
    }

    // =====================================================================
    // Payloads, shaped like the real responses
    // =====================================================================

    private static final String IG_PROFILE = """
        {"business_discovery":{"username":"%s","name":"Stub Creator",
          "biography":"Skincare and slow mornings. London.","followers_count":%d,
          "follows_count":312,"media_count":540},"id":"17841400000000001"}
        """;

    /** YouTube reports counts as strings. */
    private static final String YT_CHANNEL = """
        {"items":[{"id":"UCstubchannel%s",
          "snippet":{"title":"Stub Channel","description":"Weekly skincare reviews."},
          "statistics":{"subscriberCount":"54000","hiddenSubscriberCount":false,"videoCount":"80"},
          "contentDetails":{"relatedPlaylists":{"uploads":"UUstubuploads"}}}]}
        """;

    private static final String YT_PLAYLIST = """
        {"items":[{"snippet":{"title":"Unboxing #gifted","publishedAt":"2026-09-08T10:00:00Z"},
          "contentDetails":{"videoId":"vid%s"}}]}
        """;

    private static final String YT_VIDEOS = """
        {"items":[{"id":"vid%s",
          "statistics":{"viewCount":"28900","likeCount":"3100","commentCount":"142"}}]}
        """;
}
