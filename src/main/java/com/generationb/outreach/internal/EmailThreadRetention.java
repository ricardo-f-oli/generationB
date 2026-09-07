package com.generationb.outreach.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Requirement #37: creator correspondence.
 *
 * <p>Inbound replies are the most personal free text the platform stores — creators write about
 * rates, availability and sometimes their circumstances. Two years covers the working memory an
 * account manager needs ("what did we agree last season") without keeping it indefinitely.
 *
 * <p>The outreach recipient row survives, so the reporting counts (#31, #52) are unaffected: what
 * goes is the body of the message, not the fact that somebody replied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailThreadRetention implements RetentionSweeper {

    private final EmailThreadRepository emailThreadRepository;

    @Value("${retention.email-threads.months:24}")
    private int months;

    @Override
    public String dataset() {
        return "Outreach email threads";
    }

    @Override
    public String policy() {
        return "Message bodies deleted " + months + " months after they were received. The "
                + "record that a creator replied, and when, is kept for reporting.";
    }

    @Override
    @Transactional
    public Outcome sweep(boolean dryRun) {
        Instant before = Instant.now().minus(Duration.ofDays(months * 30L));
        int affected = dryRun
                ? emailThreadRepository.countReceivedBefore(before)
                : emailThreadRepository.deleteReceivedBefore(before);

        if (!dryRun && affected > 0) {
            log.info("Retention: deleted {} email thread(s)", affected);
        }
        return new Outcome(dataset(), policy(), affected,
                emailThreadRepository.oldestReceivedAt());
    }
}
