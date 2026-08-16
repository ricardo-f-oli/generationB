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

    @Column(name = "views", nullable = false)
    private Integer views = 0;

    @Column(name = "likes", nullable = false)
    private Integer likes = 0;

    @Column(name = "comments", nullable = false)
    private Integer comments = 0;

    @Column(name = "er", nullable = false)
    private BigDecimal er = BigDecimal.ZERO;

    @Column(name = "standardized_name", nullable = false)
    private String standardizedName;

    @Column(name = "is_unsolicited", nullable = false)
    private boolean unsolicited = false;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();
}
