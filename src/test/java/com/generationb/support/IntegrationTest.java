package com.generationb.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base for tests that need the real thing.
 *
 * <p>Q-H2: a real PostgreSQL, not H2. Half the bugs this project has hit were Postgres-specific
 * — JSONB type binding, parameter type inference on a null timestamp, partial unique indexes —
 * and every one of them would have passed against H2.
 *
 * <p>S3Mock gives the storage adapter a real S3 API to talk to. A mocked S3 client tests that we
 * called a method, not that the bucket layout, content types and presigned URLs are right.
 *
 * <p>Both containers are static, so all subclasses share one instance rather than paying the
 * startup cost per class.
 *
 * <p>Tagged {@code integration}, which keeps it out of the default {@code mvn test} run. That
 * matters because the production image is built by running Maven inside a Docker build stage,
 * and a build stage has no Docker daemon of its own — these tests cannot run there, and trying
 * produced five classes failing with {@code NoClassDefFoundError}, which reads like a
 * compilation error and is not one.
 *
 * <p>They run under {@code mvn verify}, which is what CI uses. Nothing is weakened: the image
 * build still runs the whole unit suite, so a broken commit still cannot produce a deployable
 * jar, and CI checks that these actually executed rather than silently vanishing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// Requires a Docker daemon, so it is excluded from the default `mvn test` run and executed by
// failsafe during `mvn verify` instead. @Tag is @Inherited, so every subclass is covered without
// having to remember to tag it. See the surefire/failsafe configuration in pom.xml.
@Tag("integration")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("generationb")
                    .withReuse(true);

    // S3Mock rather than MinIO: MinIO stopped publishing public images — Docker Hub dropped the
    // tags and quay.io/minio/minio now answers 401 — so every integration test failed at
    // container start. Pinned to an exact version so a new release cannot break CI unannounced.
    private static final int S3_PORT = 9090;
    private static final String S3_BUCKET = "generationb-test";

    static final GenericContainer<?> S3 =
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withExposedPorts(S3_PORT)
                    .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", S3_BUCKET)
                    .waitingFor(Wait.forListeningPort())
                    .withReuse(true);

    static {
        // Started manually rather than via @Container so both come up once for the whole suite.
        POSTGRES.start();
        S3.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("storage.provider", () -> "s3");
        registry.add("storage.endpoint",
                () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(S3_PORT));
        registry.add("storage.bucket", () -> S3_BUCKET);
        // S3Mock accepts any credentials, but S3FileStorage refuses to start without them.
        registry.add("storage.access-key", () -> "testaccess");
        registry.add("storage.secret-key", () -> "testsecret123");
        registry.add("storage.region", () -> "us-east-1");

        // Deterministic secret so tokens are stable across runs.
        registry.add("jwt.secret", () -> "test-only-secret-key-that-is-at-least-256-bits-long-abcdef");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("app.frontend-url", () -> "http://localhost:5173");
        // No outbound calls from a test run.
        registry.add("ai.api-key", () -> "");
        registry.add("resend.api-key", () -> "");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestAuth auth;

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetBrandContext() {
        com.generationb.foundation.BrandContext.clear();
    }

    /**
     * V44 puts the seeded accounts on a single-use password, and PasswordChangeGate then refuses
     * every endpoint until it is changed. {@link TestAuth} signs in as those accounts, so without
     * this the entire integration suite would 403 — every test failing for a reason none of them
     * is about.
     *
     * <p>Cleared before each test rather than once, because a test that exercises the gate
     * deliberately sets the flag back and must not leave it set for whatever runs next.
     *
     * <p>The gate itself is covered by {@code ForcedPasswordChangeIntegrationTest}, which turns
     * the flag on for one account on purpose.
     */
    @BeforeEach
    void clearTemporaryPasswordFlag() {
        jdbcTemplate.update("UPDATE users SET must_change_password = false");
    }

    /** A small file on disk, for upload tests. */
    protected static Path tempFile(String name, byte[] content) throws Exception {
        Path dir = Files.createTempDirectory("gb-test");
        Path file = dir.resolve(name);
        Files.write(file, content);
        return file;
    }
}
