package com.generationb.coverage.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoverageItemRepository extends JpaRepository<CoverageItem, UUID> {

    /**
     * Q-C1: the coverage log used to call {@code findAll()}, which returned every brand's rows to
     * whoever asked. Every read below is brand-scoped.
     *
     * <p>Q-J20: optional String filters carry a CAST guard so Postgres can infer the parameter
     * type when they are null. The date bounds take sentinel values from the service instead —
     * Postgres cannot type a bare {@code ? IS NULL} for a timestamp at all.
     */
    @Query("""
        SELECT c FROM CoverageItem c
        WHERE c.brandId = ?#{@brandContext.brandId}
          AND c.deletedAt IS NULL
          AND (CAST(:platform AS string) IS NULL OR c.platform = :platform)
          AND (CAST(:postType AS string) IS NULL OR c.postType = :postType)
          AND (:campaignId IS NULL OR c.campaignId = :campaignId)
          AND (:creatorId IS NULL OR c.creatorId = :creatorId)
          AND (:unsolicited IS NULL OR c.unsolicited = :unsolicited)
          AND (CAST(:query AS string) IS NULL
               OR LOWER(c.creatorHandle) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
               OR LOWER(c.standardizedName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
          AND c.postedAt >= :from
          AND c.postedAt <= :to
        """)
    Page<CoverageItem> search(@Param("query") String query,
                              @Param("platform") String platform,
                              @Param("postType") String postType,
                              @Param("campaignId") UUID campaignId,
                              @Param("creatorId") UUID creatorId,
                              @Param("unsolicited") Boolean unsolicited,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    @Query("""
        SELECT c FROM CoverageItem c
        WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<CoverageItem> findScopedById(@Param("id") UUID id);

    /** Requirement #11: the dedupe check for auto-clipping. */
    @Query("""
        SELECT c.url FROM CoverageItem c
        WHERE c.brandId = :brandId AND c.url IN :urls AND c.deletedAt IS NULL
        """)
    List<String> findExistingUrls(@Param("brandId") UUID brandId, @Param("urls") List<String> urls);

    /** Requirement #13: what the morning digest reports on. Not brand-scoped — the job has no user. */
    @Query("""
        SELECT c FROM CoverageItem c
        WHERE c.brandId = :brandId
          AND c.deletedAt IS NULL
          AND c.postedAt >= :since
        ORDER BY c.views DESC
        """)
    List<CoverageItem> findForDigest(@Param("brandId") UUID brandId, @Param("since") Instant since);

    @Query("""
        SELECT c FROM CoverageItem c
        WHERE c.brandId = ?#{@brandContext.brandId}
          AND c.deletedAt IS NULL
          AND (:campaignId IS NULL OR c.campaignId = :campaignId)
        ORDER BY c.postedAt DESC
        """)
    List<CoverageItem> findAllScopedForExport(@Param("campaignId") UUID campaignId);
}
