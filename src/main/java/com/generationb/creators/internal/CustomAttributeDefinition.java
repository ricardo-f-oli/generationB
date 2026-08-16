package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #16: B. admins define what is tracked per creator (birthdays, sizing, hair type,
 * topics to avoid, …), configurable per brand.
 *
 * <p>The previous model only stored attribute VALUES, with no way to define the schema, and no
 * repository or endpoint ever touched it.
 */
@Entity
@Table(name = "custom_attribute_definitions")
@Getter
@Setter
@NoArgsConstructor
public class CustomAttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "attribute_key", nullable = false)
    private String attributeKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "attribute_type", nullable = false)
    private String attributeType = "STRING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
