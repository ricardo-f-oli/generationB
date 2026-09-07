package com.generationb.gifting.internal;

import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #37: home addresses do not stay on file.
 *
 * <p>The most sensitive data in the platform. A creator gave a home address so a parcel could
 * reach them; once it has arrived, or come back, that purpose is spent and there is no basis to
 * keep holding it. Deleted rather than anonymised — a partial address is still a home address,
 * and the consent evidence lives separately in {@code consent_records}, which outlives this by
 * design so the agency can still show the address was collected lawfully.
 *
 * <p>Two windows, because there are two ways an address stops being needed:
 * <ul>
 *   <li>The parcel completed — delivered, returned or declined — {@code delivered-days} ago.
 *   <li>Nothing was ever sent to it and it has sat unused for {@code unused-days}. This catches
 *       addresses captured for a gifting run that was cancelled.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftingAddressRetention implements RetentionSweeper {

    private final GiftingAddressRepository addressRepository;

    /**
     * 90 days after the parcel completed. Long enough to investigate a delivery dispute or a
     * return that arrives late, short enough that last season's addresses are gone.
     */
    @Value("${retention.gifting-address.delivered-days:90}")
    private int deliveredDays;

    /** 180 days for an address nothing was ever dispatched to. */
    @Value("${retention.gifting-address.unused-days:180}")
    private int unusedDays;

    @Override
    public String dataset() {
        return "Gifting addresses";
    }

    @Override
    public String policy() {
        return "Deleted " + deliveredDays + " days after the parcel is delivered, returned or "
                + "declined, or " + unusedDays + " days after capture if nothing was ever "
                + "dispatched. Proof of consent is kept separately.";
    }

    @Override
    @Transactional
    public Outcome sweep(boolean dryRun) {
        List<UUID> due = addressRepository.findAddressesDueForPurge(
                cutoff(deliveredDays), cutoff(unusedDays));

        if (!due.isEmpty() && !dryRun) {
            // Chunked: a bulk delete with a few thousand ids in the IN clause is a query plan
            // nobody enjoys, and this runs unattended at 03:00.
            int deleted = 0;
            for (int from = 0; from < due.size(); from += 500) {
                deleted += addressRepository.deleteByIds(
                        due.subList(from, Math.min(from + 500, due.size())));
            }
            log.info("Retention: deleted {} gifting address(es)", deleted);
        }

        return new Outcome(dataset(), policy(), due.size(), addressRepository.oldestAddressHeld());
    }

    private static Instant cutoff(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }
}
