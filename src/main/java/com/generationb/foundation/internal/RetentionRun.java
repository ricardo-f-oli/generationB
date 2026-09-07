package com.generationb.foundation.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #37: one pass of one retention policy, recorded.
 *
 * <p>The evidence half of the obligation. Having a policy is not compliance; being able to show
 * it was applied is. The policy text is copied in rather than referenced so that changing a
 * window later does not rewrite history.
 */
@Entity
@Table(name = "retention_runs")
@Getter
@Setter
@NoArgsConstructor
public class RetentionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "dataset", nullable = false, length = 120)
    private String dataset;

    /** The rule as it stood when this ran. Deliberately a copy, not a lookup. */
    @Column(name = "policy", nullable = false, length = 500)
    private String policy;

    @Column(name = "affected", nullable = false)
    private int affected;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "oldest_remaining")
    private Instant oldestRemaining;

    /** Null for the nightly job; set when a person triggered it. */
    @Column(name = "triggered_by")
    private UUID triggeredBy;

    /** Set when the sweeper threw. A failed pass must be visible rather than simply absent. */
    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "ran_at", nullable = false, updatable = false)
    private Instant ranAt;

    @PrePersist
    void onCreate() {
        if (ranAt == null) {
            ranAt = Instant.now();
        }
    }

    public boolean failed() {
        return error != null;
    }
}
