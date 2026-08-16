package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** The VALUE of a per-brand custom attribute for one creator (requirement #16). */
@Entity
@Table(name = "creator_custom_attributes")
@Getter
@Setter
@NoArgsConstructor
public class CreatorCustomAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Column(name = "attribute_value")
    private String attributeValue;

    @Column(name = "attribute_type", nullable = false)
    private String attributeType = "STRING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
