package com.generationb.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the reporting module needs to know about coverage, without reaching into the coverage
 * module's internals.
 *
 * <p>Requirement #49 is computed entirely from these aggregates.
 */
public interface CoverageMetricsPort {

    /** Aggregate totals for a brand, optionally narrowed to one campaign and date window. */
    record CoverageStats(
        long posts,
        long views,
        long likes,
        long comments,
        long shares,
        long saves,
        /** Null when nothing was captured — the caller must not print 0% as if it were measured. */
        BigDecimal averageEngagementRate,
        long shortFormPosts,
        long longFormPosts,
        long unsolicitedPosts,
        /** Requirement #49 lists impressions, but no provider supplies them yet. */
        boolean impressionsAvailable
    ) {}

    record CreatorCoverage(
        UUID creatorId,
        String handle,
        long posts,
        long views,
        long likes,
        long comments,
        BigDecimal averageEngagementRate
    ) {}

    record TopPost(
        String handle,
        String platform,
        String postType,
        String url,
        long views,
        BigDecimal engagementRate,
        java.time.Instant postedAt
    ) {}

    CoverageStats stats(UUID brandId, UUID campaignId, LocalDate from, LocalDate to);

    List<CreatorCoverage> creatorBreakdown(UUID brandId, UUID campaignId, LocalDate from, LocalDate to);

    List<TopPost> topPosts(UUID brandId, UUID campaignId, LocalDate from, LocalDate to, int limit);

    /** Creator ids that posted at least once in the window — half of requirement #15. */
    List<UUID> creatorsWhoPosted(UUID brandId, UUID campaignId, LocalDate from, LocalDate to);
}
