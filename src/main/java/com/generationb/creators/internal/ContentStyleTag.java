package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Requirement #17: a configurable tag library, scoped per brand. */
@Entity
@Table(name = "content_style_tags")
@Getter
@Setter
@NoArgsConstructor
public class ContentStyleTag {

    public static final String AESTHETIC = "AESTHETIC";
    public static final String CONTENT_FORMAT = "CONTENT_FORMAT";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category = AESTHETIC;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
