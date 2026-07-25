package com.generationb.foundation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {
    @Id
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "previous_value", columnDefinition = "jsonb")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;
}
