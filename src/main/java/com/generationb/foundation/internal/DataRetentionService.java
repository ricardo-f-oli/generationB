package com.generationb.foundation.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #37: runs every module's retention policy and records that it did.
 *
 * <p>It holds no policy of its own. Each {@link RetentionSweeper} decides what its data is for and
 * how long that purpose lasts, because that judgement belongs next to the data. This class finds
 * every sweeper Spring registered, runs each one through {@link RetentionRunner} in its own
 * transaction, and makes sure one failure cannot stop the rest or erase the evidence of what
 * already succeeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    /** Every sweeper any module registered. A new module joins the nightly pass for free. */
    private final List<RetentionSweeper> sweepers;
    private final RetentionRunner runner;
    private final RetentionRunRepository runRepository;

    /**
     * An off switch that leaves previews working. Useful on a restored copy of production, where
     * running the real sweep would delete data that still exists in the live system.
     */
    @Value("${retention.enabled:true}")
    private boolean enabled;

    /** Six years: the evidence has to outlive every dataset it reports on. */
    @Value("${retention.run-log.years:6}")
    private int runLogYears;

    /**
     * @param dryRun      count what would go without changing anything
     * @param triggeredBy the admin who pressed the button, or null for the nightly schedule
     */
    public List<RetentionSweeper.Outcome> runAll(boolean dryRun, UUID triggeredBy) {
        if (!enabled && !dryRun) {
            log.warn("Retention is switched off (retention.enabled=false); nothing was removed");
            return List.of();
        }

        List<RetentionSweeper.Outcome> outcomes = new ArrayList<>();
        for (RetentionSweeper sweeper : sweepers) {
            outcomes.add(runner.run(sweeper, dryRun, triggeredBy));
        }

        int total = outcomes.stream().mapToInt(RetentionSweeper.Outcome::affected).sum();
        log.info("Retention pass{}: {} record(s) across {} dataset(s)",
                dryRun ? " (preview)" : "", total, outcomes.size());

        if (!dryRun) {
            int pruned = runner.pruneRunLog(
                    Instant.now().minus(Duration.ofDays(runLogYears * 365L)));
            if (pruned > 0) {
                log.info("Retention: pruned {} expired retention-run record(s)", pruned);
            }
        }
        return outcomes;
    }

    /** The most recent pass for each dataset — what the GDPR screen leads with. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public List<RetentionRun> latestPerDataset() {
        return runRepository.findLatestPerDataset();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public List<RetentionRun> recentRuns(int limit) {
        return runRepository.findRecent(PageRequest.of(0, Math.clamp(limit, 1, 200)));
    }

    /**
     * What each sweeper says it does, whether or not it has ever run.
     *
     * <p>This is the list that should match the agency's privacy notice, so it is generated from
     * the code rather than written out separately and allowed to drift.
     */
    public List<RetentionSweeper.Outcome> declaredPolicies() {
        return sweepers.stream()
                .map(s -> RetentionSweeper.Outcome.of(s.dataset(), s.policy(), 0))
                .sorted(Comparator.comparing(RetentionSweeper.Outcome::dataset))
                .toList();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
