package com.generationb.reporting;

import java.math.BigDecimal;
import java.util.UUID;

/** Requirement #55: the client-set KPIs a shortlisted creator is measured against. */
public record KpiTargetResponse(
    UUID campaignId,
    Integer minFollowers,
    Integer maxFollowers,
    BigDecimal minEr,
    BigDecimal minUkAudience,
    Long targetReach,
    String preferredPlatform,
    String preferredNiche
) {}
