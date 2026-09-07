package com.generationb.foundation.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Requirement #37: the nightly retention pass.
 *
 * <p>03:00 Europe/London — after the coverage digest window and well before anyone is working, so
 * a long delete on a large table is not competing with a person waiting for a page to load.
 *
 * <p>Deliberately not a startup task as well. Deleting personal data is not something that should
 * happen every time somebody restarts the service to check a config change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final DataRetentionService retentionService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/London")
    public void applyRetentionPolicies() {
        try {
            // null: nobody triggered this, the schedule did. The evidence row records that.
            retentionService.runAll(false, null);
        } catch (Exception e) {
            // Individual sweepers already isolate their own failures; this is the last resort.
            log.error("The retention pass failed", e);
        }
    }
}
