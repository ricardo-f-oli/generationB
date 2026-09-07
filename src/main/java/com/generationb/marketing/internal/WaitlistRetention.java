package com.generationb.marketing.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Requirement #37: waitlist sign-ups that never confirmed.
 *
 * <p>The landing page is double opt-in (#48): an address is only consented once the person clicks
 * the link in the confirmation email. An entry that never got that click has no lawful basis
 * behind it, so it is deleted rather than kept as a "maybe".
 *
 * <p>Confirmed entries are untouched — those people asked to hear from the agency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistRetention implements RetentionSweeper {

    private final WaitlistRepository waitlistRepository;

    /** 30 days is generous for clicking a link that was emailed immediately. */
    @Value("${retention.waitlist.unconfirmed-days:30}")
    private int unconfirmedDays;

    @Override
    public String dataset() {
        return "Unconfirmed waitlist sign-ups";
    }

    @Override
    public String policy() {
        return "Deleted " + unconfirmedDays + " days after sign-up when the double opt-in email "
                + "was never confirmed. Confirmed sign-ups are kept.";
    }

    @Override
    @Transactional
    public Outcome sweep(boolean dryRun) {
        Instant before = Instant.now().minus(Duration.ofDays(unconfirmedDays));
        int affected = dryRun
                ? waitlistRepository.countUnconfirmedBefore(before)
                : waitlistRepository.deleteUnconfirmedBefore(before);

        if (!dryRun && affected > 0) {
            log.info("Retention: deleted {} unconfirmed waitlist sign-up(s)", affected);
        }
        return Outcome.of(dataset(), policy(), affected);
    }
}
