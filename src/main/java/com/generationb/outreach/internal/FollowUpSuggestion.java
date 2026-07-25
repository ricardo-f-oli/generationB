package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "follow_up_suggestions")
@Getter
@Setter
public class FollowUpSuggestion extends BaseEntity {

    @Column(name = "outreach_recipient_id", nullable = false)
    private UUID outreachRecipientId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;
}
