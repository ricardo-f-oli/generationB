package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Requirement #49 asks for follower growth. A single mutable {@code followers_count} can never
 * answer that, so a daily snapshot is kept and growth is the difference between two points.
 */
@Entity
@Table(name = "creator_follower_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class CreatorFollowerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "followers_count", nullable = false)
    private Integer followersCount;

    @Column(name = "er_percentage")
    private BigDecimal erPercentage;

    @Column(name = "captured_on", nullable = false)
    private LocalDate capturedOn = LocalDate.now();

    public static CreatorFollowerSnapshot of(UUID creatorId, Integer followers, BigDecimal er) {
        CreatorFollowerSnapshot snapshot = new CreatorFollowerSnapshot();
        snapshot.creatorId = creatorId;
        snapshot.followersCount = followers == null ? 0 : followers;
        snapshot.erPercentage = er;
        return snapshot;
    }
}
