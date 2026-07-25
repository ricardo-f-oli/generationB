package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.outreach.RecipientStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outreach_recipients")
@Getter
@Setter
public class OutreachRecipient extends BaseEntity {

    @Column(name = "outreach_campaign_id", nullable = false)
    private UUID outreachCampaignId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "creator_email")
    private String creatorEmail;

    @Column(name = "creator_first_name")
    private String creatorFirstName;

    @Column(name = "creator_handle")
    private String creatorHandle;

    @Column(name = "resolved_subject")
    private String resolvedSubject;

    @Column(name = "resolved_body")
    private String resolvedBody;

    @Column(name = "sendgrid_message_id")
    private String sendgridMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecipientStatus status = RecipientStatus.NOT_SENT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "follow_up_suggested_at")
    private Instant followUpSuggestedAt;

    @Column(name = "follow_up_sent_at")
    private Instant followUpSentAt;
}
