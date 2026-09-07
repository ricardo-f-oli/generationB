package com.generationb.foundation.storage;

import com.generationb.foundation.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cloudflare R2, driven through the S3 API.
 *
 * <p>R2 was chosen over S3 for the free tier and, more importantly, no egress fees — this stores
 * campaign video, and S3 would bill for every download.
 *
 * <p>The same class also drives MinIO in tests and any other S3-compatible store, which is the
 * point: {@code storage.endpoint} is the only thing that changes.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3FileStorage implements FileStoragePort {

    @Value("${storage.endpoint:}")
    private String endpoint;

    @Value("${storage.bucket:}")
    private String bucket;

    @Value("${storage.access-key:}")
    private String accessKey;

    @Value("${storage.secret-key:}")
    private String secretKey;

    /** R2 ignores the region but the SDK insists on one. */
    @Value("${storage.region:auto}")
    private String region;

    private S3Client client;
    private S3Presigner presigner;

    @PostConstruct
    void connect() {
        if (!configured()) {
            log.warn("storage.provider=s3 but the credentials are incomplete; uploads will fail");
            return;
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

        // Path-style access: R2 and MinIO both expect bucket-in-path, not bucket-as-subdomain.
        S3Configuration config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(config)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .serviceConfiguration(config)
                .build();

        ensureBucket();
        log.info("Object storage ready: bucket '{}'", bucket);
    }

    @PreDestroy
    void disconnect() {
        if (client != null) client.close();
        if (presigner != null) presigner.close();
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public StoredFile upload(UUID brandId, String category, String filename, String contentType,
                             long sizeBytes, InputStream content) {
        requireReady();
        StorageKeys.validate(contentType, sizeBytes);

        String safeName = StorageKeys.safeFilename(filename);
        String key = StorageKeys.build(brandId, category, safeName);

        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(sizeBytes)
                            // Kept so a download can restore the original name without a
                            // database round trip.
                            .metadata(java.util.Map.of("original-filename", safeName))
                            .build(),
                    RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception e) {
            log.error("Upload failed for brand {}: {}", brandId, e.awsErrorDetails().errorMessage());
            throw ApiException.conflict("Could not store that file. Please try again.");
        }

        return new StoredFile(key, safeName, contentType, sizeBytes);
    }

    @Override
    public FileContent download(UUID brandId, String key) {
        requireReady();
        StorageKeys.requireOwnedBy(brandId, key);

        try {
            var response = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            var meta = response.response();
            String filename = meta.metadata().getOrDefault("original-filename", "download");
            return new FileContent(response, filename, meta.contentType(),
                    meta.contentLength() == null ? 0 : meta.contentLength());
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("File");
        } catch (S3Exception e) {
            log.error("Download failed for key {}: {}", key, e.awsErrorDetails().errorMessage());
            throw ApiException.conflict("Could not read that file.");
        }
    }

    @Override
    public Optional<String> signedUrl(UUID brandId, String key, Duration validFor) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        StorageKeys.requireOwnedBy(brandId, key);
        try {
            var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(validFor)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build());
            return Optional.of(presigned.url().toString());
        } catch (Exception e) {
            log.warn("Could not sign a URL for {}; falling back to streaming", key);
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID brandId, String key) {
        requireReady();
        StorageKeys.requireOwnedBy(brandId, key);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            // A file that is already gone is the desired state, not a failure.
            log.warn("Delete failed for key {}: {}", key, e.awsErrorDetails().errorMessage());
        }
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            // Covers NoSuchBucket and the 404 that MinIO returns for a missing bucket.
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created storage bucket '{}'", bucket);
            } catch (S3Exception create) {
                log.warn("Could not verify or create bucket '{}': {}", bucket,
                        create.awsErrorDetails().errorMessage());
            }
        }
    }

    private boolean configured() {
        return notBlank(endpoint) && notBlank(bucket) && notBlank(accessKey) && notBlank(secretKey);
    }

    private void requireReady() {
        if (!isEnabled()) {
            throw ApiException.unprocessable(
                    "File storage is not configured for this environment.");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
