package com.generationb.foundation.storage;

import com.generationb.foundation.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Filesystem storage, for local development only.
 *
 * <p>Deliberately the default when {@code storage.provider} is unset, so a developer can clone
 * the repo and upload a file without an R2 account.
 *
 * <p><b>Not viable in production.</b> Render's free tier gives each instance an ephemeral disk
 * that is wiped on every deploy and not shared between instances, so anything written here
 * disappears. The prod profile sets {@code storage.provider=s3} for exactly this reason.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStoragePort {

    private final Path root;

    public LocalFileStorage(@Value("${storage.local-path:./data/uploads}") String localPath) {
        this.root = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Local file storage at {} — development only, not durable", root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the upload directory at " + root, e);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public StoredFile upload(UUID brandId, String category, String filename, String contentType,
                             long sizeBytes, InputStream content) {
        StorageKeys.validate(contentType, sizeBytes);

        String safeName = StorageKeys.safeFilename(filename);
        String key = StorageKeys.build(brandId, category, safeName);
        Path target = resolve(key);

        try {
            Files.createDirectories(target.getParent());
            long written = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            // Content type is not a filesystem concept, so it is kept alongside the file.
            Files.writeString(sidecar(target), contentType + "\n" + safeName);
            return new StoredFile(key, safeName, contentType, written);
        } catch (IOException e) {
            log.error("Could not write {}", target, e);
            throw ApiException.conflict("Could not store that file.");
        }
    }

    @Override
    public FileContent download(UUID brandId, String key) {
        StorageKeys.requireOwnedBy(brandId, key);
        Path target = resolve(key);

        if (!Files.isRegularFile(target)) {
            throw ApiException.notFound("File");
        }
        try {
            String contentType = "application/octet-stream";
            String filename = target.getFileName().toString();
            Path sidecar = sidecar(target);
            if (Files.isRegularFile(sidecar)) {
                String[] lines = Files.readString(sidecar).split("\n", 2);
                contentType = lines[0].trim();
                if (lines.length > 1 && !lines[1].isBlank()) {
                    filename = lines[1].trim();
                }
            }
            return new FileContent(Files.newInputStream(target), filename, contentType,
                    Files.size(target));
        } catch (IOException e) {
            throw ApiException.conflict("Could not read that file.");
        }
    }

    /** No signing locally: the caller streams through the API instead. */
    @Override
    public Optional<String> signedUrl(UUID brandId, String key, Duration validFor) {
        return Optional.empty();
    }

    @Override
    public void delete(UUID brandId, String key) {
        StorageKeys.requireOwnedBy(brandId, key);
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(sidecar(target));
        } catch (IOException e) {
            log.warn("Could not delete {}", target, e);
        }
    }

    /**
     * Resolves a key under the root and refuses anything that escapes it.
     *
     * <p>{@link StorageKeys#requireOwnedBy} already rejects "..", but this is the last line of
     * defence: on a filesystem a traversal is arbitrary file read, so it is checked twice.
     */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw ApiException.badRequest("That file reference is not valid.");
        }
        return resolved;
    }

    private Path sidecar(Path target) {
        return target.resolveSibling(target.getFileName() + ".meta");
    }
}
