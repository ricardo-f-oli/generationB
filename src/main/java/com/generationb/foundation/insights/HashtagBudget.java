package com.generationb.foundation.insights;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The 30-unique-hashtags-per-7-days allowance, tracked and enforced.
 *
 * <p>Directly analogous to the credit guard on the old vendor client, and for the same reason: a
 * shared, exhaustible resource that a careless loop can empty, where the failure mode is a brand
 * silently stopping receiving mention coverage rather than anything visibly breaking.
 *
 * <p>The important subtlety is what counts. Meta charges for <em>distinct</em> tags in the
 * window, not for calls. Sweeping the same three tags every hour for a week costs three of the
 * thirty. Adding a fourth brand with three new tags costs three more. So the thing to protect is
 * the introduction of a new tag, and re-use is free — which is why a resolved id is cached rather
 * than looked up again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HashtagBudget {

    /** Meta's figure. Configurable only so a test can shrink it. */
    @Value("${insights.meta.hashtag-limit:30}")
    private int limit;

    /**
     * Leave a couple of slots free. A brand adding a competitor tag mid-week should not fail
     * because a bulk sweep used the last one.
     */
    @Value("${insights.meta.hashtag-reserve:2}")
    private int reserve;

    private final InstagramHashtagLookupRepository repository;
    private final MetaGraphClient meta;

    public record Status(int used, int limit, int remaining, Instant nextSlotFreesAt) {
    }

    @Transactional(readOnly = true)
    public Status status() {
        int used = repository.countUsedSince(windowStart());
        Instant oldest = repository.oldestInWindow(windowStart());
        return new Status(used, limit, Math.max(0, limit - used),
                // A slot frees when the oldest tag in the window passes seven days old.
                oldest == null ? null : oldest.plus(Duration.ofDays(7)));
    }

    /**
     * The Graph node id for a hashtag, resolving it if this is the first time inside the window.
     *
     * @return empty when the tag is new and the allowance is spent — the caller reports that
     *         rather than silently returning no posts, because "no coverage" and "we could not
     *         look" are different answers.
     */
    @Transactional
    public Optional<String> resolve(String rawTag, UUID brandId) {
        String tag = normalise(rawTag);
        if (tag == null) {
            return Optional.empty();
        }

        // Already inside the window: free, and the id is already known.
        Optional<InstagramHashtagLookup> known = repository.findByHashtag(tag);
        if (known.isPresent()) {
            InstagramHashtagLookup lookup = known.get();
            lookup.setLastUsedAt(Instant.now());
            lookup.setUseCount(lookup.getUseCount() + 1);
            repository.save(lookup);
            return Optional.of(lookup.getHashtagId());
        }

        int used = repository.countUsedSince(windowStart());
        if (used + 1 > limit - reserve) {
            log.warn("Instagram hashtag allowance: {} of {} unique tags used in the last 7 days. "
                    + "Refusing to add \"{}\" and keep {} in reserve.", used, limit, tag, reserve);
            return Optional.empty();
        }

        Optional<String> resolved = meta.hashtagId(tag);
        resolved.ifPresent(id -> {
            repository.save(new InstagramHashtagLookup(tag, id, brandId));
            log.info("Instagram hashtag \"{}\" added to the 7-day allowance ({} of {} now used)",
                    tag, used + 1, limit);
        });
        return resolved;
    }

    /** True when this tag can be swept without spending a new slot. */
    @Transactional(readOnly = true)
    public boolean isAlreadyInWindow(String rawTag) {
        String tag = normalise(rawTag);
        return tag != null && repository.findByHashtag(tag).isPresent();
    }

    private static Instant windowStart() {
        return Instant.now().minus(Duration.ofDays(7));
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase(java.util.Locale.UK)
                .replaceFirst("^#", "")
                .replaceAll("[^\\p{L}\\p{N}_]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
