package com.generationb.foundation.insights;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that decides where Instagram profiles come from.
 *
 * <p>Today that is Meta's Business Discovery, which is free and is switched on by setting
 * {@code META_ACCESS_TOKEN} and {@code META_IG_USER_ID}. Until those exist, Instagram lookups
 * return nothing and follower counts and posts are entered by hand.
 *
 * <p>Everything downstream — profile refresh, auto-clipping, coverage, reports — reads the
 * source-neutral {@link InstagramProfile}, so another {@link InstagramProfileSource} could be added
 * here later without touching any of it.
 */
@Service
public class InstagramProfiles {

    private record Cached(InstagramProfile profile, Instant at) {
    }

    private final InstagramProfileSource meta;

    /** A refresh and a clip moments apart ask for the same profile; one lookup serves both. */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Value("${insights.instagram.cache-minutes:30}")
    private int cacheMinutes;

    public InstagramProfiles(MetaInstagramProfileSource meta) {
        this.meta = meta;
    }

    public Optional<InstagramProfileSource> active() {
        return meta.isEnabled() ? Optional.of(meta) : Optional.empty();
    }

    public boolean isEnabled() {
        return active().isPresent();
    }

    /** {@code META}, or null when Instagram is not configured. */
    public String activeName() {
        return active().map(InstagramProfileSource::name).orElse(null);
    }

    /** @param rawHandle the handle as stored; validated here before it reaches any source */
    public Optional<InstagramProfile> fetch(String rawHandle, int posts) {
        String handle = MetaGraphClient.handleOf(rawHandle);
        Optional<InstagramProfileSource> source = active();
        if (handle == null || source.isEmpty()) {
            return Optional.empty();
        }
        String key = source.get().name() + ":" + handle.toLowerCase() + ":" + posts;
        Cached cached = cache.get(key);
        if (cached != null && cached.at().isAfter(Instant.now().minus(Duration.ofMinutes(cacheMinutes)))) {
            return Optional.of(cached.profile());
        }
        Optional<InstagramProfile> found = source.get().fetch(handle, posts);
        found.ifPresent(profile -> cache.put(key, new Cached(profile, Instant.now())));
        return found;
    }

    /** A forced refresh must not read a cached answer. */
    public void evict(String rawHandle) {
        String handle = MetaGraphClient.handleOf(rawHandle);
        if (handle != null) {
            cache.keySet().removeIf(key -> key.contains(":" + handle.toLowerCase() + ":"));
        }
    }
}
