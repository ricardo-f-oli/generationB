package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_notes")
@Getter
@Setter
@NoArgsConstructor
public class CreatorNote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    /** Notes belong to the brand that wrote them, even though the creator is global. */
    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "note_text", nullable = false, columnDefinition = "text")
    private String noteText;

    @Column(name = "is_confidential", nullable = false)
    private boolean confidential = false;

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
