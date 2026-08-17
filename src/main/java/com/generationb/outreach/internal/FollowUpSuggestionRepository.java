package com.generationb.outreach.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowUpSuggestionRepository extends JpaRepository<FollowUpSuggestion, UUID> {

    @Query("""
        SELECT s FROM FollowUpSuggestion s
        WHERE s.brandId = ?#{@brandContext.brandId}
          AND s.deletedAt IS NULL
          AND (CAST(:status AS string) IS NULL OR s.status = :status)
        ORDER BY s.createdAt DESC
        """)
    List<FollowUpSuggestion> findAllScoped(@Param("status") String status);

    @Query("""
        SELECT s FROM FollowUpSuggestion s
        WHERE s.id = :id AND s.brandId = ?#{@brandContext.brandId} AND s.deletedAt IS NULL
        """)
    Optional<FollowUpSuggestion> findScopedById(@Param("id") UUID id);

    /** Guards the daily pass against creating a second suggestion for the same recipient. */
    boolean existsByOutreachRecipientIdAndStatus(UUID outreachRecipientId, String status);
}
