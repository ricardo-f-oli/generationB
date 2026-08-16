package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomAttributeDefinitionRepository extends JpaRepository<CustomAttributeDefinition, UUID> {

    @Query("""
        SELECT d FROM CustomAttributeDefinition d
        WHERE d.brandId = :brandId AND d.active = true
        ORDER BY d.displayOrder, d.label
        """)
    List<CustomAttributeDefinition> findActiveForBrand(@Param("brandId") UUID brandId);

    @Query("SELECT d FROM CustomAttributeDefinition d WHERE d.id = :id AND d.brandId = :brandId")
    Optional<CustomAttributeDefinition> findScopedById(@Param("id") UUID id, @Param("brandId") UUID brandId);

    Optional<CustomAttributeDefinition> findByBrandIdAndAttributeKey(UUID brandId, String attributeKey);
}
