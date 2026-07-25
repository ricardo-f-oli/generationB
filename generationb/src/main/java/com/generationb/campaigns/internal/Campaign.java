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
}
