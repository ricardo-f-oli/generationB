package com.generationb.reporting;

/**
 * How often a report is produced.
 *
 * <p>The names are the values stored in {@code reports.cadence} and enforced by a check
 * constraint, so they must match {@code V27}/{@code V31}. The frontend supplies the
 * human-facing labels — {@code CAMPAIGN} reads as "at campaign end".
 */
public enum ReportCadence {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    CAMPAIGN,
    AD_HOC
}
