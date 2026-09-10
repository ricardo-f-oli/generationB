package com.generationb.foundation.insights;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One hashtag we have resolved with Meta, and when we first did.
 *
 * <p>Instagram allows 30 <em>unique</em> hashtags per rolling 7 days, per app — not per brand.
 * With several client brands each monitoring their own tags plus a competitor or two, that
 * allowance is the binding constraint on mention discovery and it is shared between all of them.
 *
 * <p>Persisted rather than held in memory because the window is seven days long and a restart
 * must not appear to reset it. Re-querying a tag already inside the window costs nothing, so this
 * doubles as the id cache that makes a repeat sweep free.
 */
@Entity
@Table(name = "instagram_hashtag_lookups")
@Getter
@Setter
@NoArgsConstructor
public class InstagramHashtagLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Lower case, no leading hash. */
    @Column(name = "hashtag", nullable = false, length = 150)
    private String hashtag;

    /** Meta's node id. Stable, so holding it avoids paying to resolve the tag again. */
    @Column(name = "hashtag_id", nullable = false, length = 64)
    private String hashtagId;

    /** What the 7-day window is measured from. */
    @Column(name = "first_used_at", nullable = false)
    private Instant firstUsedAt = Instant.now();

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    @Column(name = "use_count", nullable = false)
    private int useCount = 1;

    /** Who pulled it into the shared allowance, for when it runs short. */
    @Column(name = "first_used_by_brand")
    private UUID firstUsedByBrand;

    public InstagramHashtagLookup(String hashtag, String hashtagId, UUID brandId) {
        this.hashtag = hashtag;
        this.hashtagId = hashtagId;
        this.firstUsedByBrand = brandId;
    }
}
