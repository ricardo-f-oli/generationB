package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByCreatorIdOrderByRecordedAtDesc(UUID creatorId);

    @Query("""
        SELECT c FROM ConsentRecord c
        WHERE c.withdrawnAt IS NULL
        ORDER BY c.recordedAt DESC
        """)
    List<ConsentRecord> findActiveConsents();

    @Query("""
        SELECT COUNT(c) > 0 FROM ConsentRecord c
        WHERE c.creatorId = :creatorId
          AND c.consentType = :type
          AND c.granted = true
          AND c.withdrawnAt IS NULL
        """)
    boolean hasActiveConsent(@Param("creatorId") UUID creatorId, @Param("type") String type);
}
