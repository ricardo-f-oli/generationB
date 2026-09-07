package com.generationb.foundation.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Runs one sweeper in its own transaction and writes the evidence row.
 *
 * <p>A separate bean rather than a method on {@link DataRetentionService} because
 * {@code REQUIRES_NEW} is the entire point: calling it from a sibling method of the same class
 * would go straight through the object, skip the proxy, and quietly give every sweeper the
 * caller's single shared transaction. One deadlock would then roll back the whole night's work
 * <em>including</em> the records saying what had already been removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionRunner {

    private final RetentionRunRepository runRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetentionSweeper.Outcome run(RetentionSweeper sweeper, boolean dryRun, UUID triggeredBy) {
        try {
            RetentionSweeper.Outcome outcome = sweeper.sweep(dryRun);
            record(outcome, dryRun, triggeredBy, null);
            return outcome;

        } catch (Exception e) {
            // A retention job that fails silently looks exactly like one that found nothing, and
            // the second is what everybody assumes.
            log.error("Retention sweep failed for {}", sweeper.dataset(), e);
            RetentionSweeper.Outcome failed =
                    RetentionSweeper.Outcome.of(sweeper.dataset(), sweeper.policy(), 0);
            record(failed, dryRun, triggeredBy, e.getClass().getSimpleName());
            return failed;
        }
    }

    /** Also its own transaction, so a failure here cannot undo a successful sweep. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int pruneRunLog(Instant before) {
        return runRepository.deleteRanBefore(before);
    }

    private void record(RetentionSweeper.Outcome outcome, boolean dryRun, UUID triggeredBy,
                        String error) {
        RetentionRun run = new RetentionRun();
        run.setDataset(truncate(outcome.dataset(), 120));
        run.setPolicy(truncate(outcome.policy(), 500));
        run.setAffected(outcome.affected());
        run.setDryRun(dryRun);
        run.setOldestRemaining(outcome.oldestRemaining());
        run.setTriggeredBy(triggeredBy);
        // Class name only: an exception message can quote the row, and the row is the personal
        // data. The full stack trace is in the server log.
        run.setError(truncate(error, 500));
        runRepository.save(run);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
