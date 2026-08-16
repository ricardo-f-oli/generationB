package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShortlistRepository extends JpaRepository<Shortlist, UUID> {

    /**
     * Q-C2 + requirement #27: brand-scoped, and private shortlists are only visible to the
     * user who created them.
     */
    @Query("""
        SELECT s FROM Shortlist s
        WHERE s.brandId = :brandId
          AND s.deletedAt IS NULL
          AND (s.visibility = 'TEAM' OR s.createdBy = :userId)
        ORDER BY s.createdAt DESC
        """)
    List<Shortlist> findVisible(@Param("brandId") UUID brandId, @Param("userId") UUID userId);

    @Query("""
        SELECT s FROM Shortlist s
        WHERE s.id = :id AND s.brandId = :brandId AND s.deletedAt IS NULL
          AND (s.visibility = 'TEAM' OR s.createdBy = :userId)
        """)
    Optional<Shortlist> findVisibleById(@Param("id") UUID id,
                                        @Param("brandId") UUID brandId,
                                        @Param("userId") UUID userId);
}
