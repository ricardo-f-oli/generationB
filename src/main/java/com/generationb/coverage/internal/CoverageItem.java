package com.generationb.coverage.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coverage_items")
@Getter
@Setter
@NoArgsConstructor
public class CoverageItem extends BaseEntity {

    public static final String MANUAL = "MANUAL";
    public static final String AUTO_CLIP = "AUTO_CLIP";
    public static final String MENTION = "MENTION";
    public static final String IMPORT = "IMPORT";

    /** Requirement #49: the short/long form split the monthly report reports on. */
    public static final String SHORT_FORM = "SHORT";
    public static final String LONG_FORM = "LONG";

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "creator_handle", nullable = false)
    private String creatorHandle;

    @Column(name = "platform", nullable = false)
    private String platform = "INSTAGRAM";

    @Column(name = "post_type", nullable = false)
    private String postType = "REEL";

    @Column(name = "url")
    private String url;

    @Column(name = "caption", length = 1000)
    private String caption;

    /** How this row got here: MANUAL, AUTO_CLIP, MENTION or IMPORT. */
    @Column(name = "source", nullable = false)
    private String source = MANUAL;

    /** The provider's own id for the post, so a re-run recognises what it has already seen. */
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "views", nullable = false)
    private Long views = 0L;

    @Column(name = "likes", nullable = false)
    private Long likes = 0L;

    @Column(name = "comments", nullable = false)
    private Long comments = 0L;

    @Column(name = "shares")
    private Long shares;

    @Column(name = "saves")
    private Long saves;

    /** Requirement #49: short vs long form split. Derived from post type on write. */
    @Column(name = "content_form")
    private String contentForm;

    /** No provider supplies impressions yet; nullable so a report can say "not measured". */
    @Column(name = "impressions")
    private Long impressions;

    @Column(name = "er", nullable = false)
    private BigDecimal er = BigDecimal.ZERO;

    @Column(name = "standardized_name", nullable = false)
    private String standardizedName;

    @Column(name = "is_unsolicited", nullable = false)
    private boolean unsolicited = false;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();

    /**
     * A story or a reel is short form; a YouTube video or a blog post is long form. Derived on
     * write so the reporting query can aggregate without re-deciding the rule.
     */
    public static String formFor(String postType) {
        if (postType == null) {
            return SHORT_FORM;
        }
        return switch (postType.toUpperCase()) {
            case "YOUTUBE", "YOUTUBE_VIDEO", "BLOG", "PODCAST", "LONGFORM", "IGTV" -> LONG_FORM;
            default -> SHORT_FORM;
        };
    }

    /** Total engagements — the numerator of the engagement rate. */
    public long engagements() {
        return nz(likes) + nz(comments) + nz(shares) + nz(saves);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
