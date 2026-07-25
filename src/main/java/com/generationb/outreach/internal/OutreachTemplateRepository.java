package com.generationb.outreach.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutreachTemplateRepository extends JpaRepository<OutreachTemplate, UUID> {

    @Query("SELECT t FROM OutreachTemplate t WHERE (t.brandId = :brandId OR t.brandId IS NULL) AND t.isActive = true AND t.deletedAt IS NULL")
    List<OutreachTemplate> findActiveTemplatesForBrand(@Param("brandId") UUID brandId);

    @Query("SELECT t FROM OutreachTemplate t WHERE t.isActive = true AND t.deletedAt IS NULL")
    List<OutreachTemplate> findAllActiveTemplates();

    Optional<UUID> findBrandIdById(UUID id);
}
