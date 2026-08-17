package com.generationb.reporting;

import java.util.List;
import java.util.UUID;

/**
 * Requirement #55: how well one creator matches the campaign's KPIs, and why.
 *
 * <p>The brief asks this to "justify shortlists to clients without spreadsheets", so the reasons
 * matter as much as the score — a bare percentage explains nothing.
 */
public record KpiMatchResponse(
    UUID creatorId,
    String handle,
    /** 0-100. */
    int score,
    /** STRONG, PARTIAL or WEAK — drives the colour of the indicator. */
    String band,
    List<Criterion> criteria
) {
    public record Criterion(String label, boolean met, String detail) {}
}
