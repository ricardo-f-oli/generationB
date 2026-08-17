package com.generationb.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #49: the standard metric set.
 *
 * <p>Fields that cannot be measured from any data we hold are {@code null}, never zero. A report
 * that prints "0 impressions" reads as a real result; "not tracked" is the truth. The
 * {@link #notes} list carries those caveats through to the rendered report.
 */
public record ReportMetrics(
    // --- volume ---
    long posts,
    long views,
    long likes,
    long comments,
    long shares,
    long saves,

    /** Sum of views. A true reach figure needs unique-viewer data no provider gives us. */
    long estimatedReach,

    /** Null until a provider supplies impressions. */
    Long impressions,

    /** Weighted average engagement rate across the period, or null if nothing was captured. */
    BigDecimal averageEngagementRate,

    /** Requirement #49: engagement rate against the KPI target. Null when no target is set. */
    BigDecimal engagementRateVsTarget,

    // --- growth ---
    /** Total net follower change across the creators in this report. */
    Integer followerGrowth,
    BigDecimal followerGrowthPct,

    // --- content mix ---
    long shortFormPosts,
    long longFormPosts,
    long unsolicitedPosts,

    // --- quality ---
    /** Distribution of the creators' quality bands, e.g. {"A": 3, "B": 1}. */
    java.util.Map<String, Long> qualityBands,

    /** Null: conversion needs affiliate or UTM tracking that is not in place. */
    BigDecimal conversionRate,

    // --- reconciliation (requirement #15) ---
    Reconciliation reconciliation,

    List<CreatorRow> creatorBreakdown,
    List<TopPost> topPosts,

    /** Human-readable caveats about anything that could not be measured. */
    List<String> notes
) {

    /** Requirement #15: who was sent to, who posted, who did not. */
    public record Reconciliation(
        long sentTo,
        long posted,
        long notPosted,
        BigDecimal postRate,
        List<PendingCreator> outstanding
    ) {}

    public record PendingCreator(UUID creatorId, String handle, String insightStatus) {}

    public record CreatorRow(
        UUID creatorId,
        String handle,
        long posts,
        long views,
        long likes,
        long comments,
        BigDecimal engagementRate,
        Integer followerGrowth,
        String qualityBand,
        /** Requirement #52: PENDING, CHASED, RECEIVED or WAIVED. */
        String insightStatus
    ) {}

    public record TopPost(
        String handle,
        String platform,
        String postType,
        String url,
        long views,
        BigDecimal engagementRate
    ) {}
}
