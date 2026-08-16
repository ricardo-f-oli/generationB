package com.generationb.gifting.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gifting_addresses")
@Getter
@Setter
@NoArgsConstructor
public class GiftingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "country", nullable = false)
    private String country = "UK";

    @Column(name = "gdpr_consent_flag", nullable = false)
    private boolean gdprConsentFlag = true;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt = Instant.now();
}
