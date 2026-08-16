package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #21: an opt-out suppresses the creator across ALL brands, permanently.
 */
@Entity
@Table(name = "global_suppression_list")
@Getter
@Setter
@NoArgsConstructor
public class GlobalSuppression {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "email")
    private String email;

    @Column(name = "handle")
    private String handle;

    @Column(name = "reason")
    private String reason = "Opt-out requested";

    /** Where it came from: UNSUBSCRIBE_LINK, SPAM_REPORT, BOUNCE, ERASURE, MANUAL. */
    @Column(name = "source", nullable = false)
    private String source = "MANUAL";

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "opted_out_at", nullable = false)
    private Instant optedOutAt = Instant.now();
}
