package com.generationb.creators.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByHandleIgnoreCase(String handle);

    Optional<Creator> findByEmailIgnoreCase(String email);

    /**
     * Q-G1 / requirement #23 + #24 + #26: previously every creator was loaded into memory and
     * filtered with {@code String.contains}. This does the work in the database, against indexes,
     * and returns a page.
     *
     * <p>All filters are optional — a null parameter disables that clause.
     */
    /*
     * Every optional String parameter is CAST on both sides of its null check. Without the cast
     * on the `IS NULL` branch Postgres cannot infer the parameter's type when it is the only
     * usage, and the query fails at runtime with "could not determine data type of parameter".
     */
    @Query("""
        SELECT DISTINCT c FROM Creator c
        WHERE c.deletedAt IS NULL
          AND (CAST(:q AS string) IS NULL OR
               LOWER(c.handle)   LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
               LOWER(c.name)     LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
               LOWER(COALESCE(c.location, '')) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
               LOWER(COALESCE(c.niche, ''))    LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) OR
               LOWER(COALESCE(c.bio, ''))      LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
          AND (CAST(:platform AS string) IS NULL OR UPPER(c.primaryPlatform) = UPPER(CAST(:platform AS string)))
          AND (CAST(:location AS string) IS NULL OR LOWER(COALESCE(c.location, '')) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%')))
          AND (CAST(:niche AS string) IS NULL OR LOWER(COALESCE(c.niche, '')) LIKE LOWER(CONCAT('%', CAST(:niche AS string), '%')))
          AND (CAST(:minFollowers AS integer) IS NULL OR c.followersCount >= :minFollowers)
          AND (CAST(:maxFollowers AS integer) IS NULL OR c.followersCount <= :maxFollowers)
          AND (CAST(:minEr AS big_decimal) IS NULL OR c.erPercentage >= :minEr)
          AND (CAST(:minUkAudience AS big_decimal) IS NULL OR c.ukAudiencePct >= :minUkAudience)
          AND (CAST(:optInStatus AS string) IS NULL OR c.optInStatus = CAST(:optInStatus AS string))
          AND (:tagId IS NULL OR EXISTS (
                SELECT 1 FROM CreatorStyleTagLink l
                WHERE l.creatorId = c.id AND l.tagId = :tagId))
        """)
    Page<Creator> search(@Param("q") String q,
                         @Param("platform") String platform,
                         @Param("location") String location,
                         @Param("niche") String niche,
                         @Param("minFollowers") Integer minFollowers,
                         @Param("maxFollowers") Integer maxFollowers,
                         @Param("minEr") BigDecimal minEr,
                         @Param("minUkAudience") BigDecimal minUkAudience,
                         @Param("optInStatus") String optInStatus,
                         @Param("tagId") UUID tagId,
                         Pageable pageable);

    @Query("SELECT c FROM Creator c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Creator> findActiveById(@Param("id") UUID id);

    @Query("SELECT c FROM Creator c WHERE c.optInStatus = :status AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<Creator> findByOptInStatus(@Param("status") String status, Pageable pageable);

    @Query("SELECT c FROM Creator c WHERE c.id IN :ids AND c.deletedAt IS NULL")
    List<Creator> findAllActiveByIds(@Param("ids") List<UUID> ids);

    @Query("SELECT COUNT(c) FROM Creator c WHERE c.deletedAt IS NULL")
    long countActive();

    /** Distinct niches, for populating the filter UI without a hardcoded list. */
    @Query("SELECT DISTINCT c.niche FROM Creator c WHERE c.niche IS NOT NULL AND c.deletedAt IS NULL ORDER BY c.niche")
    List<String> findDistinctNiches();

    @Query("SELECT DISTINCT c.location FROM Creator c WHERE c.location IS NOT NULL AND c.deletedAt IS NULL ORDER BY c.location")
    List<String> findDistinctLocations();
}
