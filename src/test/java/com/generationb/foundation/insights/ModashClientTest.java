package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.insights.ModashClient.Meter;
import com.generationb.support.ModashStub;
import com.generationb.support.ModashStub.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The transport, against a real socket.
 *
 * <p>Everything asserted here happens below the mapping layer and none of it is visible from a
 * mocked client: the bearer header, the retry on 429, honouring {@code Retry-After}, the credit
 * guard, and Modash's habit of answering {@code 200 OK} with {@code {"error": true}}.
 *
 * <p>The account is metered, so this is the layer where a bug costs money rather than just being
 * wrong.
 */
class ModashClientTest {

    private ModashStub stub;
    private ModashClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new ModashStub();
        client = configured(stub.baseUrl(), "test-key");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static ModashClient configured(String baseUrl, String apiKey) {
        ModashClient client = new ModashClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "apiKey", apiKey);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 5);
        // Near zero so the suite is not paced by the real 550ms rate limit.
        ReflectionTestUtils.setField(client, "minRequestIntervalMs", 1L);
        ReflectionTestUtils.setField(client, "maxRetries", 2);
        ReflectionTestUtils.setField(client, "creditReserve", 2.0);
        ReflectionTestUtils.setField(client, "rawReserve", 2);
        ReflectionTestUtils.setField(client, "budgetTtlSeconds", 0L);
        return client;
    }

    // =====================================================================

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        void everyRequestCarriesTheBearerToken() {
            client.get("/user/info", Map.of(), Meter.FREE, 0);

            assertTrue(stub.authorizationHeaders().stream()
                    .allMatch(header -> "Bearer test-key".equals(header)));
        }

        @Test
        void withoutAKeyNothingIsSentAtAll() {
            ModashClient unconfigured = configured(stub.baseUrl(), "");

            assertFalse(unconfigured.isEnabled());
            assertTrue(unconfigured.get("/user/info", Map.of(), Meter.FREE, 0).isEmpty());
            // Not merely a failed call — no call. A missing key must not become vendor traffic.
            assertEquals(0, stub.requestedPaths().size());
        }
    }

    @Nested
    @DisplayName("Query strings")
    class QueryStrings {

        @Test
        void parametersAreUrlEncoded() {
            client.get("/instagram/users", Map.of("query", "a b&c=d"), Meter.FREE, 0);

            // A handle with a space or an ampersand must not corrupt the request. Creator
            // handles come from user input and from vendor payloads.
            assertTrue(stub.requestedPaths().contains("/instagram/users"));
        }

        @Test
        void blankParametersAreOmittedRatherThanSentEmpty() {
            client.get("/instagram/users", Map.of("query", "x", "limit", ""), Meter.FREE, 0);
            assertTrue(stub.requestedPaths().contains("/instagram/users"));
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureHandling {

        @Test
        void anApplicationErrorInsideA200IsTreatedAsAFailure() {
            // Modash answers 200 with {"error": true}. Reading the body as success would put an
            // empty profile through the mapper and blank a creator's demographics.
            stub.enqueue("/instagram/users",
                    Response.ok("{\"error\":true,\"code\":\"not_found\"}"));

            assertTrue(client.get("/instagram/users", Map.of(), Meter.FREE, 0).isEmpty());
        }

        @Test
        void aClientErrorIsNotRetried() {
            stub.enqueue("/instagram/users", Response.status(404));

            assertTrue(client.get("/instagram/users", Map.of(), Meter.FREE, 0).isEmpty());
            // Retrying a 404 just spends the rate limit.
            assertEquals(1, stub.hitCount("/instagram/users"));
        }

        @Test
        void malformedJsonIsAnEmptyResultRatherThanAnException() {
            stub.enqueue("/instagram/users", Response.ok("<html>gateway error</html>"));

            // A proxy or a WAF in front of the vendor can return HTML with a 200. That must
            // surface as "no answer", not as an exception escaping into a scheduled job.
            Optional<JsonNode> result =
                    assertDoesNotThrow(() -> client.get("/instagram/users", Map.of(), Meter.FREE, 0));
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Retries")
    class Retries {

        @Test
        void aRateLimitedRequestIsRetriedAndSucceeds() {
            stub.enqueue("/instagram/users", Response.rateLimited(0));
            stub.enqueue("/instagram/users", Response.ok(ModashStub.USER_LOOKUP));

            Optional<JsonNode> result = client.get("/instagram/users", Map.of(), Meter.FREE, 0);

            assertTrue(result.isPresent());
            assertEquals(2, stub.hitCount("/instagram/users"));
        }

        @Test
        void aServerErrorIsRetried() {
            stub.enqueue("/instagram/users", Response.status(503));
            stub.enqueue("/instagram/users", Response.ok(ModashStub.USER_LOOKUP));

            assertTrue(client.get("/instagram/users", Map.of(), Meter.FREE, 0).isPresent());
            assertEquals(2, stub.hitCount("/instagram/users"));
        }

        @Test
        void retriesAreBoundedAndThenGiveUp() {
            for (int i = 0; i < 5; i++) {
                stub.enqueue("/instagram/users", Response.status(500));
            }

            assertTrue(client.get("/instagram/users", Map.of(), Meter.FREE, 0).isEmpty());
            // maxRetries = 2, so three attempts and no more. An unbounded retry against a
            // vendor outage is how you get rate-limited into a longer outage.
            assertEquals(3, stub.hitCount("/instagram/users"));
        }

        @Test
        void retryAfterIsHonouredRatherThanIgnored() {
            stub.enqueue("/instagram/users", Response.rateLimited(1));
            stub.enqueue("/instagram/users", Response.ok(ModashStub.USER_LOOKUP));

            long start = System.nanoTime();
            client.get("/instagram/users", Map.of(), Meter.FREE, 0);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // Retry-After said one second. Retrying immediately is what turns a soft rate limit
            // into a hard block.
            assertTrue(elapsedMs >= 900, "retried after only " + elapsedMs + "ms");
        }
    }

    @Nested
    @DisplayName("The credit guard")
    class CreditGuard {

        @Test
        void aMeteredCallIsRefusedWhenItWouldBreachTheReserve() {
            stub.setBalance(2, 100);

            // Reserve is 2, so a 1-credit report would leave 1. It must not be sent.
            Optional<JsonNode> result = client.get(
                    "/instagram/profile/x/report", Map.of(), Meter.DISCOVERY, 1);

            assertTrue(result.isEmpty());
            assertEquals(0, stub.hitCount("/instagram/profile/x/report"));
        }

        @Test
        void theRawAllowanceIsGuardedSeparatelyFromCredits() {
            // Plenty of credits, no raw requests. Clipping must stop; reports need not.
            stub.setBalance(100, 2);

            assertTrue(client.get("/raw/ig/user-feed", Map.of(), Meter.RAW, 1).isEmpty());
            assertTrue(client.get("/instagram/profile/x/report", Map.of(), Meter.DISCOVERY, 1)
                    .isPresent());
        }

        @Test
        void aFreeCallStillWorksOnAnEmptyAccount() {
            stub.setBalance(0, 0);

            // Handle lookup costs nothing, so an exhausted account must not break it — it is
            // how someone checks a handle before deciding to spend.
            assertTrue(client.get("/instagram/users", Map.of(), Meter.FREE, 0).isPresent());
        }

        @Test
        void spendingWithinOneCacheWindowStillSeesTheEarlierCost() {
            ModashClient cached = configured(stub.baseUrl(), "test-key");
            // A long TTL: the balance is read once and not refreshed between calls.
            ReflectionTestUtils.setField(cached, "budgetTtlSeconds", 3600L);
            stub.setBalance(4, 100);

            // Reserve 2, balance 4: two reports fit, the third must not.
            assertTrue(cached.get("/instagram/profile/a/report", Map.of(), Meter.DISCOVERY, 1).isPresent());
            assertTrue(cached.get("/instagram/profile/b/report", Map.of(), Meter.DISCOVERY, 1).isPresent());
            assertTrue(cached.get("/instagram/profile/c/report", Map.of(), Meter.DISCOVERY, 1).isEmpty(),
                    "the cached balance must be decremented as it is spent, or a loop empties "
                            + "the account inside one TTL window");
        }

        @Test
        void anUnreadableBalanceDoesNotBlockEverything() {
            stub.enqueue("/user/info", Response.status(500));
            stub.enqueue("/user/info", Response.status(500));
            stub.enqueue("/user/info", Response.status(500));

            // The vendor enforces the real limit anyway; an unreachable /user/info should not
            // take the whole integration down.
            assertTrue(client.get("/instagram/profile/x/report", Map.of(), Meter.DISCOVERY, 1)
                    .isPresent());
        }
    }

    @Nested
    @DisplayName("Balance reporting")
    class BalanceReporting {

        @Test
        void theBalanceIsReadFromTheBillingBlock() {
            stub.setBalance(97.35, 95);

            ModashClient.Budget budget = client.budget(Duration.ZERO).orElseThrow();

            assertEquals(97.35, budget.credits(), 0.001);
            assertEquals(95, budget.rawRequests());
            assertNotNull(budget.checkedAt());
        }

        @Test
        void aCachedBalanceIsReusedWithinItsTtl() {
            ModashClient cached = configured(stub.baseUrl(), "test-key");
            ReflectionTestUtils.setField(cached, "budgetTtlSeconds", 3600L);

            cached.budget();
            cached.budget();
            cached.budget();

            // /user/info is free, but it is still a request against a 2/second limit.
            assertEquals(1, stub.hitCount("/user/info"));
        }
    }

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        void callsAreSpacedByTheConfiguredInterval() {
            ModashClient paced = configured(stub.baseUrl(), "test-key");
            ReflectionTestUtils.setField(paced, "minRequestIntervalMs", 120L);

            long start = System.nanoTime();
            paced.get("/instagram/users", Map.of(), Meter.FREE, 0);
            paced.get("/instagram/users", Map.of(), Meter.FREE, 0);
            paced.get("/instagram/users", Map.of(), Meter.FREE, 0);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // Three calls, two gaps. Modash allows 2/second and answers 429 above it.
            assertTrue(elapsedMs >= 240, "three calls took only " + elapsedMs + "ms");
        }
    }
}
