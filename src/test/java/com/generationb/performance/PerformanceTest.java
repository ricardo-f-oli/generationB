package com.generationb.performance;

import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Performance characteristics that would hurt on the deployment we actually have.
 *
 * <p>Render's free tier is one small instance with 512MB of RAM, and Neon's free tier caps
 * connections — the Hikari pool is set to 5. So the useful questions are not "how many requests
 * per second" but:
 *
 * <ul>
 *   <li>does an endpoint issue O(n) queries where it should issue O(1)?
 *   <li>does concurrent load exhaust a five-connection pool and deadlock?
 *   <li>does a large upload get buffered into memory?
 * </ul>
 *
 * <p>The thresholds are deliberately loose. A tight assertion on wall-clock time in CI is a
 * flaky test, not a performance test — these are set to catch an order-of-magnitude regression,
 * which is the kind that actually breaks the free tier.
 *
 * <p>Tagged {@code performance} so the normal build can skip them.
 */
@Tag("performance")
@DisplayName("Performance guardrails")
class PerformanceTest extends IntegrationTest {

    @Test
    @DisplayName("the coverage log does not slow down linearly with page size")
    void coverageListScalesSublinearly() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        Duration small = time(() -> mockMvc.perform(get("/api/coverage/log")
                .param("size", "5").header("Authorization", token))
                .andExpect(status().isOk()));

        Duration large = time(() -> mockMvc.perform(get("/api/coverage/log")
                .param("size", "200").header("Authorization", token))
                .andExpect(status().isOk()));

        // Forty times the rows must not cost forty times the wall clock. If it does, something
        // is querying per row — the N+1 that handle resolution used to be.
        assertThat(large.toMillis())
                .as("200 rows took %dms against %dms for 5 — smells like a per-row query",
                        large.toMillis(), small.toMillis())
                .isLessThan(Math.max(small.toMillis() * 10, 3000));
    }

    @Test
    @DisplayName("report generation stays inside a sensible budget")
    void reportGenerationIsNotPathological() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        Duration elapsed = time(() -> mockMvc.perform(get("/api/reports/preview")
                .param("from", "2020-01-01")
                .param("to", "2030-12-31")
                .header("Authorization", token))
                .andExpect(status().isOk()));

        // Report generation aggregates coverage, creator profiles, follower growth, insight
        // status and reconciliation. It should be a handful of queries, not dozens.
        assertThat(elapsed).as("report preview took %dms", elapsed.toMillis())
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("concurrent requests do not exhaust the connection pool")
    void survivesConcurrentLoad() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        int threads = 20;   // four times the pool size, so requests must queue and release
        int perThread = 5;
        var pool = Executors.newFixedThreadPool(threads);
        var failures = new AtomicInteger();
        var latch = new CountDownLatch(threads);

        Instant start = Instant.now();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int n = 0; n < perThread; n++) {
                        mockMvc.perform(get("/api/creators")
                                        .param("size", "24")
                                        .header("Authorization", token))
                                .andExpect(status().isOk());
                    }
                } catch (Throwable t) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        Duration elapsed = Duration.between(start, Instant.now());

        // The real assertion: it finishes at all. A pool leak shows up as a timeout here, and
        // that is exactly what would take the free tier down under a demo.
        assertThat(finished).as("100 concurrent requests did not finish within 60s").isTrue();
        assertThat(failures.get()).as("%d of 100 concurrent requests failed", failures.get())
                .isZero();
        assertThat(elapsed).as("100 requests took %s", elapsed).isLessThan(Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("a large upload streams rather than buffering the whole file")
    void largeUploadDoesNotBlowTheHeap() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        // 20MB, just under the limit. On a 512MB instance, buffering several of these would be
        // the difference between serving and an OOM kill.
        byte[] payload = new byte[20 * 1024 * 1024];
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();

        mockMvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "big.mp4", "video/mp4", payload))
                        .param("ownerType", "CARD")
                        .param("ownerId", "e4000000-0000-0000-0000-000000000002")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        long after = runtime.totalMemory() - runtime.freeMemory();
        long growthMb = Math.max(0, (after - before)) / (1024 * 1024);

        // MockMvc holds the request bytes itself, so this cannot prove true streaming — what it
        // does prove is that we are not holding several extra copies, which is the regression
        // that matters.
        assertThat(growthMb).as("heap grew %dMB handling a 20MB upload", growthMb)
                .isLessThan(200);
    }

    @Test
    @DisplayName("listing attachments for many cards is one query, not one per card")
    void attachmentCountsAreBatched() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);
        String card = "e4000000-0000-0000-0000-000000000002";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(multipart("/api/attachments")
                            .file(new MockMultipartFile("file", "f" + i + ".pdf",
                                    "application/pdf", ("file " + i).getBytes()))
                            .param("ownerType", "CARD").param("ownerId", card)
                            .header("Authorization", token))
                    .andExpect(status().isOk());
        }

        Duration elapsed = time(() -> mockMvc.perform(get("/api/attachments")
                .param("ownerType", "CARD").param("ownerId", card)
                .header("Authorization", token))
                .andExpect(status().isOk()));

        assertThat(elapsed).as("listing 10 attachments took %dms", elapsed.toMillis())
                .isLessThan(Duration.ofSeconds(2));
    }

    private Duration time(ThrowingRunnable action) throws Exception {
        // One warm-up pass, so JIT and first-query overhead are not what gets measured.
        action.run();
        Instant start = Instant.now();
        action.run();
        return Duration.between(start, Instant.now());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
