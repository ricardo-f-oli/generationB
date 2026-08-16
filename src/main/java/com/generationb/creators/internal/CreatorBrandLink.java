package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Which client brands a (global) creator has been engaged with, and in what state.
 *
 * <p>This is the table that makes the brief's "shared creator data across brands for cross-brand
 * intel" requirement work: the creator record is shared, participation is per brand.
 */
@Entity
@Table(name = "creator_brand_links")
@Getter
@Setter
@NoArgsConstructor
public class CreatorBrandLink {

    public static final String PROSPECT = "PROSPECT";
    public static final String CONTACTED = "CONTACTED";
    public static final String WORKED_WITH = "WORKED_WITH";
    public static final String BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "relationship_status", nullable = false)
    private String relationshipStatus = PROSPECT;

    @Column(name = "first_engaged_at")
    private Instant firstEngagedAt;

    @Column(name = "last_engaged_at")
    private Instant lastEngagedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static CreatorBrandLink of(UUID creatorId, UUID brandId, String status) {
        CreatorBrandLink link = new CreatorBrandLink();
        link.creatorId = creatorId;
        link.brandId = brandId;
        link.relationshipStatus = status;
        link.firstEngagedAt = Instant.now();
        link.lastEngagedAt = Instant.now();
        return link;
    }
}
