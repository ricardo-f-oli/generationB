package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentStyleTagRepository extends JpaRepository<ContentStyleTag, UUID> {

    @Query("SELECT t FROM ContentStyleTag t WHERE t.brandId = :brandId ORDER BY t.category, t.name")
    List<ContentStyleTag> findAllForBrand(@Param("brandId") UUID brandId);

    @Query("SELECT t FROM ContentStyleTag t WHERE t.id = :id AND t.brandId = :brandId")
    Optional<ContentStyleTag> findScopedById(@Param("id") UUID id, @Param("brandId") UUID brandId);

    @Query("SELECT t FROM ContentStyleTag t WHERE t.brandId = :brandId AND LOWER(t.name) = LOWER(:name)")
    Optional<ContentStyleTag> findByBrandAndName(@Param("brandId") UUID brandId, @Param("name") String name);

    @Query("SELECT COUNT(l) FROM CreatorStyleTagLink l WHERE l.tagId = :tagId")
    long countCreators(@Param("tagId") UUID tagId);
}
