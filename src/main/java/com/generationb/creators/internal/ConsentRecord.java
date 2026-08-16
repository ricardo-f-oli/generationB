package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #37 / Q-I5 / Q-F23: lawful basis recorded per consent, with timestamp and source.
 * Previously the registration form required a consent checkbox and then discarded it, and
 * gifting addresses defaulted {@code gdpr_consent_flag} to true.
 */
@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord {

    public static final String MARKETING_EMAIL = "MARKETING_EMAIL";
    public static final String DATA_STORAGE = "DATA_STORAGE";
    public static final String GIFTING_ADDRESS = "GIFTING_ADDRESS";

    public static final String CONSENT = "CONSENT";
    public static final String LEGITIMATE_INTEREST = "LEGITIMATE_INTEREST";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "subject_email")
    private String subjectEmail;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(name = "lawful_basis", nullable = false)
    private String lawfulBasis = CONSENT;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion = "v1";

    @Column(name = "granted", nullable = false)
    private boolean granted = true;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    public static ConsentRecord grant(UUID creatorId, String email, String type,
                                      String basis, String source, String ip) {
        ConsentRecord record = new ConsentRecord();
        record.creatorId = creatorId;
        record.subjectEmail = email;
        record.consentType = type;
        record.lawfulBasis = basis;
        record.source = source;
        record.sourceIp = ip;
        return record;
    }
}
