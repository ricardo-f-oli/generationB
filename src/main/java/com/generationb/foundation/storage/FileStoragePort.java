package com.generationb.foundation.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Where uploaded files live.
 *
 * <p>Two rules the implementations are required to hold, because getting either wrong is a data
 * breach rather than a bug:
 *
 * <ol>
 *   <li><b>Every key is prefixed with the brand id.</b> A caller cannot construct a key that
 *       reaches another tenant's files, because it does not build keys at all — this port does.
 *   <li><b>Nothing is publicly readable.</b> Downloads go through a time-limited signed URL or
 *       stream through the API. A public bucket would make every attachment guessable.
 * </ol>
 */
public interface FileStoragePort {

    /**
     * A stored object's identity. {@code key} is opaque to callers — treat it as a handle, not a
     * path, because its shape is the storage layer's business.
     */
    record StoredFile(String key, String filename, String contentType, long sizeBytes) {}

    /** Streamed content plus the metadata needed to serve it back. */
    record FileContent(InputStream stream, String filename, String contentType, long sizeBytes)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            stream.close();
        }
    }

    /**
     * Stores a file under the given brand.
     *
     * @param category a short slug grouping the file ("attachments", "reports", "logos"),
     *                 used only to keep the bucket navigable
     * @param filename the original name, which is sanitised before it reaches storage
     */
    StoredFile upload(UUID brandId, String category, String filename, String contentType,
                      long sizeBytes, InputStream content);

    /** Streams a file back. The caller must close it. */
    FileContent download(UUID brandId, String key);

    /**
     * A time-limited URL the browser can fetch directly, so large downloads do not occupy an
     * application thread. Implementations that cannot sign URLs return empty and the caller
     * falls back to streaming.
     */
    java.util.Optional<String> signedUrl(UUID brandId, String key, Duration validFor);

    void delete(UUID brandId, String key);

    /** Whether a usable provider is configured — screens use this to explain themselves. */
    boolean isEnabled();
}
