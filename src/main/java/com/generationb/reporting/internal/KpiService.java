package com.generationb.reporting.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.reporting.KpiMatchResponse;
import com.generationb.reporting.KpiTargetResponse;
import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Requirement #55: how well a shortlisted creator matches the campaign's client-set KPIs.
 *
 * <p>The brief's stated purpose is to "justify shortlists to clients without spreadsheets", so
 * every criterion carries a human-readable reason. A bare score would not justify anything.
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    private static final NumberFormat COMPACT = NumberFormat.getIntegerInstance(Locale.UK);

    private final CampaignKpiTargetRepository kpiRepository;
    private final CreatorLookupPort creatorLookup;

    @Transactional(readOnly = true)
    public KpiTargetResponse get(UUID campaignId) {
        return kpiRepository.findByCampaignId(campaignId)
                .map(this::toResponse)
                .orElse(new KpiTargetResponse(campaignId, null, null, null, null, null, null, null));
    }

    @Transactional
    public KpiTargetResponse upsert(UUID campaignId, KpiTargetResponse input) {
        UUID brandId = BrandContext.requireBrandId();
        CampaignKpiTarget target = kpiRepository.findByCampaignId(campaignId)
                .orElseGet(() -> {
                    CampaignKpiTarget created = new CampaignKpiTarget();
                    created.setBrandId(brandId);
                    created.setCampaignId(campaignId);
                    return created;
                });

        target.setMinFollowers(input.minFollowers());
        target.setMaxFollowers(input.maxFollowers());
        target.setMinEr(input.minEr());
        target.setMinUkAudience(input.minUkAudience());
        target.setTargetReach(input.targetReach());
        target.setPreferredPlatform(input.preferredPlatform());
        target.setPreferredNiche(input.preferredNiche());

        return toResponse(kpiRepository.save(target));
    }

    /** Scores a set of creators against the campaign KPIs. */
    @Transactional(readOnly = true)
    public List<KpiMatchResponse> match(UUID campaignId, List<UUID> creatorIds) {
        CampaignKpiTarget target = kpiRepository.findByCampaignId(campaignId).orElse(null);
        List<CreatorLookupPort.CreatorProfile> profiles = creatorLookup.profiles(creatorIds);

        if (target == null) {
            // No KPIs set: say so rather than inventing a score.
            return profiles.stream()
                    .map(p -> new KpiMatchResponse(p.creatorId(), p.handle(), 0, "UNSET",
                            List.of(new KpiMatchResponse.Criterion(
                                    "KPI targets", false,
                                    "No KPI targets have been set for this campaign yet."))))
                    .toList();
        }

        return profiles.stream().map(profile -> score(profile, target)).toList();
    }

    private KpiMatchResponse score(CreatorLookupPort.CreatorProfile profile, CampaignKpiTarget target) {
        List<KpiMatchResponse.Criterion> criteria = new ArrayList<>();

        if (target.getMinFollowers() != null || target.getMaxFollowers() != null) {
            int followers = profile.followersCount() == null ? 0 : profile.followersCount();
            boolean aboveMin = target.getMinFollowers() == null || followers >= target.getMinFollowers();
            boolean belowMax = target.getMaxFollowers() == null || followers <= target.getMaxFollowers();
            criteria.add(new KpiMatchResponse.Criterion(
                    "Follower band", aboveMin && belowMax,
                    COMPACT.format(followers) + " followers against a target of "
                            + describeRange(target.getMinFollowers(), target.getMaxFollowers())));
        }

        if (target.getMinEr() != null) {
            BigDecimal er = profile.erPercentage() == null ? BigDecimal.ZERO : profile.erPercentage();
            boolean met = er.compareTo(target.getMinEr()) >= 0;
            criteria.add(new KpiMatchResponse.Criterion(
                    "Engagement rate", met,
                    er + "% against a minimum of " + target.getMinEr() + "%"));
        }

        if (target.getMinUkAudience() != null) {
            BigDecimal uk = profile.ukAudiencePct();
            if (uk == null) {
                criteria.add(new KpiMatchResponse.Criterion(
                        "UK audience", false,
                        "Not known — audience demographics need the creator-data provider."));
            } else {
                criteria.add(new KpiMatchResponse.Criterion(
                        "UK audience", uk.compareTo(target.getMinUkAudience()) >= 0,
                        uk + "% UK against a minimum of " + target.getMinUkAudience() + "%"));
            }
        }

        if (target.getPreferredPlatform() != null && !target.getPreferredPlatform().isBlank()) {
            boolean met = target.getPreferredPlatform().equalsIgnoreCase(profile.primaryPlatform());
            criteria.add(new KpiMatchResponse.Criterion(
                    "Platform", met,
                    "Primary platform is " + profile.primaryPlatform()
                            + "; the campaign prefers " + target.getPreferredPlatform()));
        }

        if (target.getPreferredNiche() != null && !target.getPreferredNiche().isBlank()) {
            boolean met = profile.niche() != null
                    && profile.niche().toLowerCase().contains(target.getPreferredNiche().toLowerCase());
            criteria.add(new KpiMatchResponse.Criterion(
                    "Niche", met,
                    (profile.niche() == null ? "No niche recorded" : profile.niche())
                            + " against " + target.getPreferredNiche()));
        }

        if (criteria.isEmpty()) {
            return new KpiMatchResponse(profile.creatorId(), profile.handle(), 0, "UNSET",
                    List.of(new KpiMatchResponse.Criterion("KPI targets", false,
                            "No measurable KPI targets have been set.")));
        }

        long met = criteria.stream().filter(KpiMatchResponse.Criterion::met).count();
        int score = (int) Math.round((met * 100.0) / criteria.size());
        String band = score >= 80 ? "STRONG" : score >= 50 ? "PARTIAL" : "WEAK";

        return new KpiMatchResponse(profile.creatorId(), profile.handle(), score, band, criteria);
    }

    private String describeRange(Integer min, Integer max) {
        if (min != null && max != null) return COMPACT.format(min) + "–" + COMPACT.format(max);
        if (min != null) return COMPACT.format(min) + "+";
        if (max != null) return "up to " + COMPACT.format(max);
        return "any";
    }

    private KpiTargetResponse toResponse(CampaignKpiTarget target) {
        return new KpiTargetResponse(target.getCampaignId(), target.getMinFollowers(),
                target.getMaxFollowers(), target.getMinEr(), target.getMinUkAudience(),
                target.getTargetReach(), target.getPreferredPlatform(), target.getPreferredNiche());
    }
}
