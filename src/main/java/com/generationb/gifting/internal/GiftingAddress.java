package com.generationb.gifting.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A creator's delivery address.
 *
 * <p>Q-I5: consent now defaults to false and is only set when the creator ticks the box on the
 * capture form. It used to default to true for every row, which made the "GDPR consent" column
 * in the gifting log meaningless.
 */
@Entity
@Table(name = "gifting_addresses")
@Getter
@Setter
@NoArgsConstructor
public class GiftingAddress {

    public static final String SOURCE_IMPORTED = "IMPORTED";
    public static final String SOURCE_SELF_SERVE = "SELF_SERVE";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    /**
     * Which brand asked for this address. The consent wording on the public form names the
     * brand, so the record has to say which one it was.
     */
    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "street")
    private String street;

    @Column(name = "street2")
    private String street2;

    @Column(name = "city")
    private String city;

    @Column(name = "county")
    private String county;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "phone")
    private String phone;

    @Column(name = "country", nullable = false)
    private String country = "UK";

    @Column(name = "gdpr_consent_flag", nullable = false)
    private boolean gdprConsentFlag = false;

    @Column(name = "consent_source", nullable = false)
    private String consentSource = SOURCE_IMPORTED;

    /** Requirement #41: the single-use link the creator follows to fill the form in. */
    @Column(name = "capture_token")
    private String captureToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt = Instant.now();

    /** An address row exists from the moment we ask; this says whether it has been filled in. */
    public boolean isCaptured() {
        return street != null && !street.isBlank()
                && postalCode != null && !postalCode.isBlank()
                && gdprConsentFlag;
    }
}
