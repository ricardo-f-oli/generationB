package com.generationb.creators.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Requirement #37: creators nobody has engaged with in a long time.
 *
 * <p>The one sweeper that deliberately deletes nothing. Storage limitation is a real obligation
 * and a dormant creator record is real personal data, but a cron job that quietly anonymises a
 * client's contact list because a date passed is a worse failure than holding it a month longer.
 * ICO guidance asks for a documented review, not necessarily automatic destruction.
 *
 * <p>So this counts and lists them, the GDPR screen shows the list, and an admin anonymises the
 * ones that should go using the existing right-to-erasure path — which suppresses the address as
 * well, so they are not re-imported next week.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorRetentionReview implements RetentionSweeper {

    private final CreatorRepository creatorRepository;

    /**
     * Three years. Agency relationships are long and seasonal: a creator used once for a
     * Christmas campaign is plausibly used again two Christmases later, so the two-year window
     * usual for marketing lists would throw away working contacts.
     */
    @Value("${retention.creator-review.months:36}")
    private int months;

    @Value("${retention.creator-review.max-listed:100}")
    private int maxListed;

    @Override
    public String dataset() {
        return "Dormant creator records";
    }

    @Override
    public String policy() {
        return "Flagged for review after " + months + " months with no contact, gift, coverage "
                + "or edit. Never deleted automatically \u2014 an admin decides.";
    }

    @Override
    @Transactional(readOnly = true)
    public Outcome sweep(boolean dryRun) {
        // dryRun is irrelevant here: this sweeper only ever reports.
        int due = creatorRepository.countDueForRetentionReview(cutoff());
        if (due > 0) {
            log.info("Retention: {} creator(s) due for a dormancy review", due);
        }
        return Outcome.of(dataset(), policy(), due);
    }

    /** The actual list, for the GDPR screen. */
    @Transactional(readOnly = true)
    public List<Creator> dueForReview() {
        return creatorRepository.findDueForRetentionReview(
                cutoff(), PageRequest.of(0, Math.clamp(maxListed, 1, 500)));
    }

    private Instant cutoff() {
        return Instant.now().minus(Duration.ofDays(months * 30L));
    }
}
