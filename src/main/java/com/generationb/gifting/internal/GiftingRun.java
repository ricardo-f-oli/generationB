package com.generationb.gifting.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "gifting_runs")
@Getter
@Setter
@NoArgsConstructor
public class GiftingRun extends BaseEntity {

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "mailer_text")
    private String mailerText;

    @Column(name = "comp_slip_status", nullable = false)
    private String compSlipStatus = "PENDING";
}
