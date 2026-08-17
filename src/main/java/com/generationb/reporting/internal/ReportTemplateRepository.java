package com.generationb.reporting.internal;

import com.generationb.reporting.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, UUID> {

    @Query("""
        SELECT t FROM ReportTemplate t
        WHERE t.brandId = ?#{@brandContext.brandId} AND t.deletedAt IS NULL
        ORDER BY t.reportType, t.name
        """)
    List<ReportTemplate> findAllScoped();

    @Query("""
        SELECT t FROM ReportTemplate t
        WHERE t.id = :id AND t.brandId = ?#{@brandContext.brandId} AND t.deletedAt IS NULL
        """)
    Optional<ReportTemplate> findScopedById(@Param("id") UUID id);

    @Query("""
        SELECT t FROM ReportTemplate t
        WHERE t.brandId = ?#{@brandContext.brandId}
          AND t.reportType = :type
          AND t.defaultTemplate = true
          AND t.deletedAt IS NULL
        """)
    Optional<ReportTemplate> findDefaultFor(@Param("type") ReportType type);
}
