package com.generationb.campaigns.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Requirement #9: per-user saved views (my cards, blocked, awaiting approval, due this week). */
@Entity
@Table(name = "saved_views")
@Getter
@Setter
@NoArgsConstructor
public class SavedView {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "scope", nullable = false)
    private String scope = "BOARD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> filter;

    @Column(name = "is_shared", nullable = false)
    private boolean shared = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
