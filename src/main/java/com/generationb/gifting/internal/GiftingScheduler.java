package com.generationb.gifting.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Requirement #46: the daily reminder pass.
 *
 * <p>Kept separate from {@link GiftingService} so the sequence can also be triggered by hand from
 * the UI without the schedule being involved, and so the transactional service is not proxied by
 * the scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftingScheduler {

    private final GiftingService giftingService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/London")
    public void sendGiftReminders() {
        try {
            giftingService.runReminderSequence();
        } catch (Exception e) {
            // A failed pass must not take the scheduler down for the rest of the day.
            log.error("The gifting reminder pass failed", e);
        }
    }
}
