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
}
