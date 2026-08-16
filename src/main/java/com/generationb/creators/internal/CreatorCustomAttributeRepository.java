package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorCustomAttributeRepository extends JpaRepository<CreatorCustomAttribute, UUID> {

    @Query("""
        SELECT a FROM CreatorCustomAttribute a
        WHERE a.creatorId = :creatorId AND (a.brandId IS NULL OR a.brandId = :brandId)
        """)
    List<CreatorCustomAttribute> findForCreator(@Param("creatorId") UUID creatorId,
                                                @Param("brandId") UUID brandId);

    Optional<CreatorCustomAttribute> findByCreatorIdAndDefinitionId(UUID creatorId, UUID definitionId);
}
