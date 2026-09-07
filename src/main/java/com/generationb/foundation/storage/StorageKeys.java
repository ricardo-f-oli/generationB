package com.generationb.foundation.storage;

import com.generationb.foundation.ApiException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and checks storage keys.
 *
 * <p>Separate from the adapters so both share exactly one definition of what a safe key is —
 * two implementations of "sanitise the filename" is one implementation too many.
 */
public final class StorageKeys {

    /** 25MB. Render's free tier has 512MB of RAM; a larger upload risks the whole instance. */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    /**
     * What a creator campaign actually needs to attach: images, video, and the document formats
     * briefs and contracts arrive in.
     *
     * <p>An allowlist rather than a blocklist. A blocklist is a promise to have thought of every
     * dangerous type, which nobody can keep — {@code .svg} alone carries script, and
     * {@code .html} is stored XSS if it is ever served from our origin.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/quicktime",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/csv", "text/plain");

    private StorageKeys() {
    }

    public static void validate(String contentType, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw ApiException.badRequest("That file is empty.");
        }
        if (sizeBytes > MAX_BYTES) {
            throw ApiException.badRequest(
                    "That file is larger than the " + (MAX_BYTES / 1024 / 1024) + "MB limit.");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        // Strip any "; charset=" parameter before comparing.
        int semicolon = type.indexOf(';');
        if (semicolon > 0) {
            type = type.substring(0, semicolon).trim();
        }
        if (!ALLOWED_CONTENT_TYPES.contains(type)) {
            throw ApiException.badRequest("We cannot accept that file type.");
        }
    }

    /**
     * Reduces a user-supplied filename to something safe to store and to echo back in a
     * Content-Disposition header.
     *
     * <p>Strips any directory component, so {@code ../../etc/passwd} becomes {@code passwd},
     * and removes the CR/LF that would otherwise let a crafted filename inject a second HTTP
     * header on download.
     */
    public static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        // Both separators: a Windows client sends backslashes.
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\r\\n\\u0000]", "");
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();

        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        if (base.isBlank()) {
            return "file";
        }
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    /**
     * The key a file is stored under: {@code <brand>/<category>/<uuid>-<filename>}.
     *
     * <p>The brand prefix is what makes tenant isolation structural rather than a filter someone
     * has to remember to apply. The UUID means two people uploading "brief.pdf" do not collide.
     */
    public static String build(UUID brandId, String category, String filename) {
        return brandId + "/" + slug(category) + "/" + UUID.randomUUID() + "-" + safeFilename(filename);
    }

    /**
     * Refuses a key that does not belong to this brand.
     *
     * <p>Called on every read and delete. Without it, a key from a response body could be
     * replayed against another tenant's file.
     */
    public static void requireOwnedBy(UUID brandId, String key) {
        if (key == null || brandId == null || !key.startsWith(brandId + "/")) {
            throw ApiException.notFound("File");
        }
        if (key.contains("..")) {
            throw ApiException.badRequest("That file reference is not valid.");
        }
    }

    private static String slug(String category) {
        if (category == null || category.isBlank()) {
            return "misc";
        }
        String cleaned = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return cleaned.isBlank() ? "misc" : cleaned;
    }
}
