package com.generationb.foundation.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Requirement #37: the audit trail (#36) is itself personal data — it records who changed what,
 * and its diffs quote the values.
 *
 * <p>Two years is the working compromise: long enough to answer "who changed this creator's
 * status and when" across two full campaign cycles, short enough that it is not an indefinite
 * record of staff activity. Personal fields are already redacted in the stored diff, so what
 * expires here is mostly the who-and-when.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogRetention implements RetentionSweeper {

    private final AuditLogRepository auditLogRepository;

    @Value("${retention.audit-log.months:24}")
    private int months;

    @Override
    public String dataset() {
        return "Audit trail";
    }

    @Override
    public String policy() {
        return "Entries deleted " + months + " months after they were recorded.";
    }

    @Override
    @Transactional
    public Outcome sweep(boolean dryRun) {
        Instant before = Instant.now().minus(Duration.ofDays(months * 30L));
        int affected = dryRun
                ? auditLogRepository.countBefore(before)
                : auditLogRepository.deleteBefore(before);

        if (!dryRun && affected > 0) {
            log.info("Retention: deleted {} audit entr(ies)", affected);
        }
        return new Outcome(dataset(), policy(), affected, auditLogRepository.oldestEntry());
    }
}
