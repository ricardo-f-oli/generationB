package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit entity for the {@code creator_style_tags} join table.
 *
 * <p>Q-J34: the previous {@code @ManyToMany} on {@code Creator} was never read or written, and
 * serialising the lazy collection out of a controller was a live {@code LazyInitializationException}.
 * Modelling the link explicitly lets tags be queried and filtered without loading creators.
 */
@Entity
@Table(name = "creator_style_tags")
@IdClass(CreatorStyleTagLink.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class CreatorStyleTagLink {

    @Id
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private UUID tagId;

    public CreatorStyleTagLink(UUID creatorId, UUID tagId) {
        this.creatorId = creatorId;
        this.tagId = tagId;
    }

    public static class Key implements Serializable {
        private UUID creatorId;
        private UUID tagId;

        public Key() {
        }

        public Key(UUID creatorId, UUID tagId) {
            this.creatorId = creatorId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(creatorId, key.creatorId) && Objects.equals(tagId, key.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(creatorId, tagId);
        }
    }
}
