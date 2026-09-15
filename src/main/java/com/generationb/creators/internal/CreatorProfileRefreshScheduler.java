package com.generationb.creators.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The daily refresh of follower counts for creators on ACTIVE campaigns (M-14).
 *
 * <p>Runs before the working day so the morning's reports and digest read today's figures, and
 * records a follower snapshot per refresh so growth (#49) has two points in time to compare.
 * Does nothing when neither Instagram nor YouTube is configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatorProfileRefreshScheduler {

    private final CreatorEnrichmentService enrichmentService;

    @Scheduled(cron = "${insights.enrichment.cron:0 0 6 * * *}", zone = "Europe/London")
    public void refreshActiveCampaignCreators() {
        try {
            enrichmentService.refreshActiveCampaignCreators();
        } catch (Exception e) {
            log.error("The daily creator profile refresh failed", e);
        }
    }
}
