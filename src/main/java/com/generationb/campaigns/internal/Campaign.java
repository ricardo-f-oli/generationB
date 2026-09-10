package com.generationb.campaigns.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.campaigns.CampaignStatus;
import com.generationb.campaigns.CampaignType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
public class Campaign extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false)
    private CampaignType campaignType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CampaignStatus status;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * The tag a briefed creator is asked to post with, so their post can be attributed without
     * paying for a hashtag lookup.
     *
     * <p>Instagram's hashtag search returns posts with no username attached, so a mention found
     * that way cannot be tied to anyone. Reading a creator's own posts by handle has attribution
     * built in and costs nothing extra, because auto-clipping already makes that call — it just
     * needs something unambiguous to look for. This is it.
     *
     * <p>Set once at creation and never regenerated. A tag that changed after creators had been
     * briefed would silently stop matching the posts they already made.
     */
    @Column(name = "tracking_hashtag", nullable = false, length = 100)
    private String trackingHashtag;
}
