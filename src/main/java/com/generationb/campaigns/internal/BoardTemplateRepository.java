package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardTemplateRepository extends JpaRepository<BoardTemplate, UUID> {

    @Query("""
        SELECT t FROM BoardTemplate t
        WHERE t.brandId = ?#{@brandContext.brandId} AND t.deletedAt IS NULL
        ORDER BY t.name
        """)
    List<BoardTemplate> findAllForBrand();

    @Query("""
        SELECT t FROM BoardTemplate t
        WHERE t.brandId = ?#{@brandContext.brandId}
          AND t.campaignType = :campaignType
          AND t.defaultTemplate = true
          AND t.deletedAt IS NULL
        """)
    Optional<BoardTemplate> findDefaultFor(@Param("campaignType") String campaignType);

    @Query("SELECT t FROM BoardTemplate t WHERE t.id = :id AND t.brandId = ?#{@brandContext.brandId}")
    Optional<BoardTemplate> findScopedById(@Param("id") UUID id);
}
