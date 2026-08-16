package com.generationb.coverage.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoverageDigestSettingsRepository extends JpaRepository<CoverageDigestSettings, UUID> {
    Optional<CoverageDigestSettings> findByBrandId(UUID brandId);
}
