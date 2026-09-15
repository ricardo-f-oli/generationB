package com.generationb.foundation.insights;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Instagram through Meta's Business Discovery — the permanent source once Meta approves the app. */
@Component
@RequiredArgsConstructor
public class MetaInstagramProfileSource implements InstagramProfileSource {

    private static final DateTimeFormatter META_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final MetaGraphClient meta;

    @Override
    public String name() {
        return "META";
    }

    @Override
    public boolean isEnabled() {
        return meta.isEnabled();
    }

    @Override
    public Optional<InstagramProfile> fetch(String handle, int posts) {
        return meta.businessDiscoveryMedia(handle, posts).map(node -> map(node, handle));
    }

    static InstagramProfile map(JsonNode node, String handle) {
        List<InstagramProfile.Post> posts = new ArrayList<>();
        for (JsonNode item : node.path("media").path("data")) {
            posts.add(new InstagramProfile.Post(
                    item.path("id").asText(null),
                    InstagramProfile.canonicalUrl(item.path("permalink").asText(null)),
                    item.path("caption").asText(null),
                    item.hasNonNull("like_count") ? item.path("like_count").asLong() : null,
                    item.path("comments_count").asLong(0),
                    // Business Discovery publishes no view count on any media type.
                    null,
                    postType(item.path("media_product_type").asText(""), item.path("media_type").asText("")),
                    timestamp(item.path("timestamp").asText(null))));
        }
        return new InstagramProfile(
                node.path("username").asText(handle),
                node.path("name").asText(null),
                node.path("biography").asText(null),
                node.path("followers_count").asLong(0),
                posts,
                "META");
    }

    private static String postType(String productType, String mediaType) {
        if ("REELS".equalsIgnoreCase(productType)) {
            return "REEL";
        }
        return switch (mediaType.toUpperCase()) {
            case "CAROUSEL_ALBUM" -> "CAROUSEL";
            case "VIDEO" -> "VIDEO";
            default -> "POST";
        };
    }

    private static Instant timestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Meta writes +0000 rather than Z, which Instant.parse rejects.
            return OffsetDateTime.parse(value, META_TIMESTAMP).toInstant();
        } catch (Exception first) {
            try {
                return Instant.parse(value);
            } catch (Exception second) {
                return null;
            }
        }
    }
}
