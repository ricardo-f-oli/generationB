package com.generationb.coverage.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "coverage_digest_settings")
@Getter
@Setter
@NoArgsConstructor
public class CoverageDigestSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false, unique = true)
    private UUID brandId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "send_time", nullable = false)
    private String sendTime = "08:00";

    @Column(name = "recipient_email")
    private String recipientEmail;

    /**
     * Requirement #12: the clipping-name format, per brand.
     * Placeholders: {creator} {handle} {platform} {type} {date} {brand}
     */
    @Column(name = "clipping_name_pattern", nullable = false)
    private String clippingNamePattern = "{brand}_{handle}_{platform}_{type}_{date}";

    @Column(name = "include_unsolicited", nullable = false)
    private boolean includeUnsolicited = true;

    @Column(name = "last_sent_at")
    private java.time.Instant lastSentAt;
}
