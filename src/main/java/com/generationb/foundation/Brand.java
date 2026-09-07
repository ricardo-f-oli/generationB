package com.generationb.foundation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A client brand of the agency (Q0.1). Previously the table existed with only a name, which is
 * why per-brand briefs, boards and report templates had nothing to hang off.
 */
@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug")
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_colour")
    private String primaryColour;

    @Column(name = "tone_of_voice")
    private String toneOfVoice;

    @Column(name = "brand_guidelines", columnDefinition = "text")
    private String brandGuidelines;

    @Column(name = "instagram_handle")
    private String instagramHandle;

    @Column(name = "monitored_hashtags", columnDefinition = "text")
    private String monitoredHashtags;

    @Column(name = "reply_to_email")
    private String replyToEmail;

    @Column(name = "from_name")
    private String fromName;

    // ------------------------------------------- insurance (requirement #40)

    /**
     * The brand's product liability cover. Gifting dispatch is refused when this has expired or
     * was never recorded — posting a client's product to a creator's home without confirming the
     * client is covered is the exposure the requirement is actually about.
     */
    @Column(name = "product_liability_insurer", length = 200)
    private String productLiabilityInsurer;

    @Column(name = "product_liability_policy_number", length = 100)
    private String productLiabilityPolicyNumber;

    @Column(name = "product_liability_expires_on")
    private java.time.LocalDate productLiabilityExpiresOn;

    /** Cover level in whole pounds. Recorded so a brief can state it without a phone call. */
    @Column(name = "product_liability_cover_gbp")
    private Long productLiabilityCoverGbp;

    @Column(name = "insurance_notes", length = 1000)
    private String insuranceNotes;

    /** Printed on the comp slip and shown on the address form. Per brand: cosmetics need an
     *  allergy line that a tote bag does not. */
    @Column(name = "gifting_disclaimer", columnDefinition = "text")
    private String giftingDisclaimer;

    /** True when cover is recorded and has not lapsed. The only question dispatch asks. */
    public boolean hasProductLiabilityCover() {
        return productLiabilityExpiresOn != null
                && !productLiabilityExpiresOn.isBefore(java.time.LocalDate.now());
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
