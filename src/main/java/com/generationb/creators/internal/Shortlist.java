package com.generationb.creators.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Requirement #27. Shortlists belong to a brand; creators on them are global. */
@Entity
@Table(name = "shortlists")
@Getter
@Setter
@NoArgsConstructor
public class Shortlist extends BaseEntity {

    public static final String TEAM = "TEAM";
    public static final String PRIVATE = "PRIVATE";

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "visibility", nullable = false)
    private String visibility = TEAM;
}
