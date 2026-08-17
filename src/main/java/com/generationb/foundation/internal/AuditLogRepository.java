package com.generationb.foundation.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Requirement #36: the audit trail, read back. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.brandId = ?#{@brandContext.brandId}
          AND (CAST(:entityType AS string) IS NULL OR a.entityType = :entityType)
          AND (CAST(:action AS string) IS NULL OR a.action = :action)
          AND (:entityId IS NULL OR a.entityId = :entityId)
          AND (:changedBy IS NULL OR a.changedBy = :changedBy)
          AND a.timestamp >= :from
          AND a.timestamp <= :to
        """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                          @Param("action") String action,
                          @Param("entityId") UUID entityId,
                          @Param("changedBy") UUID changedBy,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    /** Drives the filter dropdown, so it only offers types that actually appear. */
    @Query("""
        SELECT DISTINCT a.entityType FROM AuditLog a
        WHERE a.brandId = ?#{@brandContext.brandId}
        ORDER BY a.entityType
        """)
    List<String> findEntityTypes();
}
