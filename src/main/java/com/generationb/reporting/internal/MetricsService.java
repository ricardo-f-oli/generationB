package com.generationb.reporting.internal;

import com.generationb.foundation.BrandContext;
import com.generationb.reporting.ReportMetrics;
import com.generationb.shared.CoverageMetricsPort;
import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Requirement #49: the standard metric set.
 *
 * <p>The guiding rule here is that a metric we cannot measure is reported as <em>unmeasured</em>,
 * never as zero. Two of the eight requested metrics have no data source at all — impressions and
 * conversion — and both come back null with a note explaining why, rather than a plausible-looking
 * number that would end up in a client deck.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final CoverageMetricsPort coverageMetrics;
    private final CreatorLookupPort creatorLookup;
    private final InsightRequestRepository insightRepository;
    private final CampaignKpiTargetRepository kpiRepository;

    @Transactional(readOnly = true)
    public ReportMetrics compute(UUID campaignId, LocalDate from, LocalDate to) {
        UUID brandId = BrandContext.requireBrandId();
        List<String> notes = new ArrayList<>();

        CoverageMetricsPort.CoverageStats stats = coverageMetrics.stats(brandId, campaignId, from, to);
        List<CoverageMetricsPort.CreatorCoverage> breakdown =
                coverageMetrics.creatorBreakdown(brandId, campaignId, from, to);

        if (stats.posts() == 0) {
            notes.add("No coverage was captured in this period, so engagement figures are unavailable.");
        }
        if (!stats.impressionsAvailable()) {
            notes.add("Impressions are not supplied by any connected data source and are therefore not reported.");
        }
        notes.add("Conversion tracking is not configured for this brand, so conversion rate is not reported.");
        notes.add("Reach is estimated from total views; unique-viewer data is not available.");

        // ---- creator-level enrichment -------------------------------------
        List<UUID> creatorIds = breakdown.stream()
                .map(CoverageMetricsPort.CreatorCoverage::creatorId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, CreatorLookupPort.CreatorProfile> profiles = creatorLookup.profiles(creatorIds).stream()
                .collect(Collectors.toMap(CreatorLookupPort.CreatorProfile::creatorId, p -> p, (a, b) -> a));

        // Fetched once: the start totals below need the same rows.
        List<CreatorLookupPort.FollowerGrowth> growth = creatorLookup.followerGrowth(creatorIds, from, to);
        Map<UUID, Integer> growthByCreator = growth.stream()
                .collect(Collectors.toMap(CreatorLookupPort.FollowerGrowth::creatorId,
                        CreatorLookupPort.FollowerGrowth::delta, (a, b) -> a));

        Map<UUID, String> insightStatus = insightRepository.findForCampaign(brandId, campaignId).stream()
                .collect(Collectors.toMap(InsightRequest::getCreatorId, InsightRequest::getStatus, (a, b) -> a));

        List<ReportMetrics.CreatorRow> rows = breakdown.stream()
                .map(c -> {
                    CreatorLookupPort.CreatorProfile profile = profiles.get(c.creatorId());
                    return new ReportMetrics.CreatorRow(
                            c.creatorId(),
                            c.handle() != null ? c.handle()
                                    : (profile != null ? profile.handle() : "unknown"),
                            c.posts(), c.views(), c.likes(), c.comments(),
                            c.averageEngagementRate(),
                            growthByCreator.get(c.creatorId()),
                            profile != null ? profile.qualityBand() : null,
                            insightStatus.getOrDefault(c.creatorId(), InsightRequest.PENDING));
                })
                .toList();

        // ---- follower growth ----------------------------------------------
        Integer totalGrowth = null;
        BigDecimal growthPct = null;
        if (!growthByCreator.isEmpty()) {
            int delta = growthByCreator.values().stream().mapToInt(Integer::intValue).sum();
            int startTotal = growth.stream()
                    .mapToInt(CreatorLookupPort.FollowerGrowth::startFollowers).sum();
            totalGrowth = delta;
            if (startTotal > 0) {
                growthPct = BigDecimal.valueOf(delta)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(startTotal), 2, RoundingMode.HALF_UP);
            }
        } else {
            notes.add("Follower growth needs at least two snapshots in the period; none were found.");
        }

        // ---- quality band distribution ------------------------------------
        Map<String, Long> qualityBands = profiles.values().stream()
                .map(p -> p.qualityBand() == null ? "Unrated" : p.qualityBand())
                .collect(Collectors.groupingBy(b -> b, TreeMap::new, Collectors.counting()));

        // ---- reconciliation (requirement #15) ------------------------------
        ReportMetrics.Reconciliation reconciliation = reconcile(brandId, campaignId, from, to, insightStatus, profiles);

        // ---- ER against the campaign KPI target ----------------------------
        BigDecimal erVsTarget = kpiRepository.findByCampaignId(campaignId == null ? new UUID(0, 0) : campaignId)
                .map(CampaignKpiTarget::getMinEr)
                .filter(Objects::nonNull)
                .filter(target -> stats.averageEngagementRate() != null)
                .map(target -> stats.averageEngagementRate().subtract(target))
                .orElse(null);

        List<ReportMetrics.TopPost> topPosts = coverageMetrics.topPosts(brandId, campaignId, from, to, 5).stream()
                .map(p -> new ReportMetrics.TopPost(
                        p.handle(), p.platform(), p.postType(), p.url(), p.views(), p.engagementRate()))
                .toList();

        return new ReportMetrics(
                stats.posts(), stats.views(), stats.likes(), stats.comments(),
                stats.shares(), stats.saves(),
                stats.views(),                 // estimated reach
                null,                          // impressions: not measurable
                stats.averageEngagementRate(),
                erVsTarget,
                totalGrowth,
                growthPct,
                stats.shortFormPosts(), stats.longFormPosts(), stats.unsolicitedPosts(),
                qualityBands,
                null,                          // conversion: not tracked
                reconciliation,
                rows,
                topPosts,
                notes);
    }

    /**
     * Requirement #15: who was sent to, who posted, who has not. This is the metric the brief
     * calls "closing the loop on the seeding metric".
     */
    private ReportMetrics.Reconciliation reconcile(UUID brandId, UUID campaignId,
                                                   LocalDate from, LocalDate to,
                                                   Map<UUID, String> insightStatus,
                                                   Map<UUID, CreatorLookupPort.CreatorProfile> knownProfiles) {
        List<UUID> sentTo = creatorLookup.creatorsSentTo(brandId, campaignId, from, to);
        Set<UUID> posted = new HashSet<>(coverageMetrics.creatorsWhoPosted(brandId, campaignId, from, to));

        List<UUID> outstandingIds = sentTo.stream().filter(id -> !posted.contains(id)).toList();

        Map<UUID, CreatorLookupPort.CreatorProfile> profiles = new HashMap<>(knownProfiles);
        List<UUID> unknown = outstandingIds.stream().filter(id -> !profiles.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            creatorLookup.profiles(unknown).forEach(p -> profiles.put(p.creatorId(), p));
        }

        List<ReportMetrics.PendingCreator> outstanding = outstandingIds.stream()
                .map(id -> new ReportMetrics.PendingCreator(
                        id,
                        profiles.containsKey(id) ? profiles.get(id).handle() : "unknown",
                        insightStatus.getOrDefault(id, InsightRequest.PENDING)))
                .toList();

        long sentCount = sentTo.size();
        long postedCount = sentTo.stream().filter(posted::contains).count();
        BigDecimal postRate = sentCount == 0 ? null
                : BigDecimal.valueOf(postedCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(sentCount), 1, RoundingMode.HALF_UP);

        return new ReportMetrics.Reconciliation(
                sentCount, postedCount, outstandingIds.size(), postRate, outstanding);
    }
}
