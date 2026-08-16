package com.generationb.creators;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requirement #23/#24/#26: free-text plus the classic filters (platform, location, niche,
 * follower band, ER, UK audience %, aesthetic tag).
 */
public record CreatorSearchCriteria(
    String q,
    String platform,
    String location,
    String niche,
    Integer minFollowers,
    Integer maxFollowers,
    BigDecimal minEr,
    BigDecimal minUkAudience,
    String optInStatus,
    UUID tagId
) {
    /** Blank strings from query parameters become null so the SQL predicate is skipped. */
    public CreatorSearchCriteria {
        q = blankToNull(q);
        platform = blankToNull(platform);
        location = blankToNull(location);
        niche = blankToNull(niche);
        optInStatus = blankToNull(optInStatus);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
