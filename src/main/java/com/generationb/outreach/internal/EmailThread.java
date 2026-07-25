package com.generationb.outreach.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.outreach.ThreadDirection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_threads")
@Getter
@Setter
public class EmailThread extends BaseEntity {

    @Column(name = "outreach_recipient_id", nullable = false)
    private UUID outreachRecipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private ThreadDirection direction;

    @Column(name = "sendgrid_message_id")
    private String sendgridMessageId;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
