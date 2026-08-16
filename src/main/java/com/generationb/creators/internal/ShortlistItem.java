package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shortlist_items")
@Getter
@Setter
@NoArgsConstructor
public class ShortlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "shortlist_id", nullable = false)
    private UUID shortlistId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    public static ShortlistItem of(UUID shortlistId, UUID creatorId, int position) {
        ShortlistItem item = new ShortlistItem();
        item.shortlistId = shortlistId;
        item.creatorId = creatorId;
        item.position = position;
        return item;
    }
}
