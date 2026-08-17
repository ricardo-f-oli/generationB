package com.generationb.reporting.internal;

import com.generationb.reporting.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("""
        SELECT r FROM Report r
        WHERE r.brandId = ?#{@brandContext.brandId}
          AND r.deletedAt IS NULL
          AND (:status IS NULL OR r.status = :status)
          AND (:campaignId IS NULL OR r.campaignId = :campaignId)
        ORDER BY r.createdAt DESC
        """)
    Page<Report> findAllScoped(@Param("status") ReportStatus status,
                               @Param("campaignId") UUID campaignId,
                               Pageable pageable);

    @Query("""
        SELECT r FROM Report r
        WHERE r.id = :id AND r.brandId = ?#{@brandContext.brandId} AND r.deletedAt IS NULL
        """)
    Optional<Report> findScopedById(@Param("id") UUID id);

    @Query("""
        SELECT COUNT(r) FROM Report r
        WHERE r.brandId = ?#{@brandContext.brandId}
          AND r.status = :status
          AND r.deletedAt IS NULL
        """)
    long countByStatus(@Param("status") ReportStatus status);
}
