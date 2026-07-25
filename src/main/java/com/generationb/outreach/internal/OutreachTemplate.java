package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.outreach.OutreachType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "outreach_templates")
@Getter
@Setter
public class OutreachTemplate extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OutreachType type;

    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(name = "supported_tokens", columnDefinition = "jsonb")
    private String supportedTokens;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;
}
