package com.generationb.coverage.internal;

import com.generationb.shared.CoverageMetricsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The coverage module's implementation of {@link CoverageMetricsPort}. */
@Service
@RequiredArgsConstructor
public class CoverageMetricsAdapter implements CoverageMetricsPort {

    private final CoverageMetricsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public CoverageStats stats(UUID brandId, UUID campaignId, LocalDate from, LocalDate to) {
        CoverageMetricsRepository.StatsProjection p =
                repository.aggregate(brandId, campaignId, from, to);

        if (p == null || p.getPosts() == null || p.getPosts() == 0) {
            // Nothing captured. Returning nulls rather than zeros so the report can say
            // "not measured" instead of "0%", which would read as a genuine result.
            return new CoverageStats(0, 0, 0, 0, 0, 0, null, 0, 0, 0, false);
        }

        return new CoverageStats(
                nz(p.getPosts()),
                nz(p.getViews()),
                nz(p.getLikes()),
                nz(p.getComments()),
                nz(p.getShares()),
                nz(p.getSaves()),
                p.getAvgEr(),
                nz(p.getShortForm()),
                nz(p.getLongForm()),
                nz(p.getUnsolicited()),
                // Requirement #49 asks for impressions; no provider supplies them today.
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatorCoverage> creatorBreakdown(UUID brandId, UUID campaignId,
                                                  LocalDate from, LocalDate to) {
        return repository.breakdown(brandId, campaignId, from, to).stream()
                .map(row -> new CreatorCoverage(
                        row.getCreatorId(),
                        row.getHandle(),
                        nz(row.getPosts()),
                        nz(row.getViews()),
                        nz(row.getLikes()),
                        nz(row.getComments()),
                        row.getAvgEr()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopPost> topPosts(UUID brandId, UUID campaignId, LocalDate from, LocalDate to, int limit) {
        return repository.topPosts(brandId, campaignId, from, to,
                        org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(item -> new TopPost(
                        item.getCreatorHandle(),
                        item.getPlatform(),
                        item.getPostType(),
                        item.getUrl(),
                        item.getViews() == null ? 0L : item.getViews(),
                        item.getEr(),
                        item.getPostedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> creatorsWhoPosted(UUID brandId, UUID campaignId, LocalDate from, LocalDate to) {
        return repository.distinctCreatorsWithCoverage(brandId, campaignId, from, to);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
