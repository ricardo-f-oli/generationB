package com.generationb.campaigns.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Requirements #4 and #7: stages are defined per brand and per campaign type, then instantiated
 * onto each board. The previous implementation hardcoded four stages in a Java String[].
 */
@Entity
@Table(name = "board_templates")
@Getter
@Setter
@NoArgsConstructor
public class BoardTemplate extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "campaign_type", nullable = false)
    private String campaignType;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;
}
