package com.generationb.coverage.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Requirement #13: the morning coverage digest.
 *
 * <p>Fires hourly and lets each brand's configured send time decide whether it is their turn,
 * because the send time is per brand and free-text — a single cron cannot cover them all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverageScheduler {

    private final CoverageService coverageService;

    @Scheduled(cron = "0 0 * * * *", zone = "Europe/London")
    public void sendMorningDigests() {
        try {
            int sent = coverageService.sendDigests();
            if (sent > 0) {
                log.info("Coverage digest: {} email(s) sent", sent);
            }
        } catch (Exception e) {
            log.error("The coverage digest pass failed", e);
        }
    }
}
