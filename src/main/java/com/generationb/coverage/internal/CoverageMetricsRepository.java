package com.generationb.coverage.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate queries behind the reporting metrics (#49).
 *
 * <p>Every figure is computed in the database rather than by loading rows and summing in Java —
 * a coverage log can run to tens of thousands of posts.
 */
@Repository
public interface CoverageMetricsRepository extends JpaRepository<CoverageItem, UUID> {

    interface StatsProjection {
        Long getPosts();
        Long getViews();
        Long getLikes();
        Long getComments();
        Long getShares();
        Long getSaves();
        BigDecimal getAvgEr();
        Long getShortForm();
        Long getLongForm();
        Long getUnsolicited();
    }

    interface BreakdownProjection {
        UUID getCreatorId();
        String getHandle();
        Long getPosts();
        Long getViews();
        Long getLikes();
        Long getComments();
        BigDecimal getAvgEr();
    }

    @Query(value = """
        SELECT
            COUNT(*)                                              AS posts,
            COALESCE(SUM(views), 0)                               AS views,
            COALESCE(SUM(likes), 0)                               AS likes,
            COALESCE(SUM(comments), 0)                            AS comments,
            COALESCE(SUM(shares), 0)                              AS shares,
            COALESCE(SUM(saves), 0)                               AS saves,
            ROUND(AVG(NULLIF(er, 0)), 2)                          AS avgEr,
            COUNT(*) FILTER (WHERE content_form = 'SHORT')        AS shortForm,
            COUNT(*) FILTER (WHERE content_form = 'LONG')         AS longForm,
            COUNT(*) FILTER (WHERE is_unsolicited)                AS unsolicited
        FROM coverage_items
        WHERE brand_id = :brandId
          AND deleted_at IS NULL
          AND (CAST(:campaignId AS uuid) IS NULL OR campaign_id = :campaignId)
          AND (CAST(:from AS date) IS NULL OR posted_at >= CAST(:from AS date))
          AND (CAST(:to   AS date) IS NULL OR posted_at < CAST(:to AS date) + INTERVAL '1 day')
        """, nativeQuery = true)
    StatsProjection aggregate(@Param("brandId") UUID brandId,
                              @Param("campaignId") UUID campaignId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    @Query(value = """
        SELECT
            creator_id                       AS creatorId,
            MAX(creator_handle)              AS handle,
            COUNT(*)                         AS posts,
            COALESCE(SUM(views), 0)          AS views,
            COALESCE(SUM(likes), 0)          AS likes,
            COALESCE(SUM(comments), 0)       AS comments,
            ROUND(AVG(NULLIF(er, 0)), 2)     AS avgEr
        FROM coverage_items
        WHERE brand_id = :brandId
          AND deleted_at IS NULL
          AND creator_id IS NOT NULL
          AND (CAST(:campaignId AS uuid) IS NULL OR campaign_id = :campaignId)
          AND (CAST(:from AS date) IS NULL OR posted_at >= CAST(:from AS date))
          AND (CAST(:to   AS date) IS NULL OR posted_at < CAST(:to AS date) + INTERVAL '1 day')
        GROUP BY creator_id
        ORDER BY SUM(views) DESC
        """, nativeQuery = true)
    List<BreakdownProjection> breakdown(@Param("brandId") UUID brandId,
                                        @Param("campaignId") UUID campaignId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Query("""
        SELECT c FROM CoverageItem c
        WHERE c.brandId = :brandId
          AND c.deletedAt IS NULL
          AND (:campaignId IS NULL OR c.campaignId = :campaignId)
        ORDER BY c.views DESC
        """)
    List<CoverageItem> topPosts(@Param("brandId") UUID brandId,
                                @Param("campaignId") UUID campaignId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                Pageable pageable);

    @Query(value = """
        SELECT DISTINCT creator_id
        FROM coverage_items
        WHERE brand_id = :brandId
          AND deleted_at IS NULL
          AND creator_id IS NOT NULL
          AND (CAST(:campaignId AS uuid) IS NULL OR campaign_id = :campaignId)
          AND (CAST(:from AS date) IS NULL OR posted_at >= CAST(:from AS date))
          AND (CAST(:to   AS date) IS NULL OR posted_at < CAST(:to AS date) + INTERVAL '1 day')
        """, nativeQuery = true)
    List<UUID> distinctCreatorsWithCoverage(@Param("brandId") UUID brandId,
                                            @Param("campaignId") UUID campaignId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}
