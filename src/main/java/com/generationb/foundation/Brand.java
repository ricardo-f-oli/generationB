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
