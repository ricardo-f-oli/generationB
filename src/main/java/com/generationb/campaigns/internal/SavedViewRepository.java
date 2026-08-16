package com.generationb.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

    @Query("""
        SELECT v FROM SavedView v
        WHERE v.brandId = :brandId AND (v.userId = :userId OR v.shared = true)
        ORDER BY v.name
        """)
    List<SavedView> findVisible(@Param("brandId") UUID brandId, @Param("userId") UUID userId);

    @Query("SELECT v FROM SavedView v WHERE v.id = :id AND v.brandId = :brandId AND v.userId = :userId")
    Optional<SavedView> findOwned(@Param("id") UUID id,
                                  @Param("brandId") UUID brandId,
                                  @Param("userId") UUID userId);
}
