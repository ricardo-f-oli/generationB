package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiftingRunRepository extends JpaRepository<GiftingRun, UUID> {

    @Query("""
        SELECT r FROM GiftingRun r
        WHERE r.brandId = ?#{@brandContext.brandId} AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
        """)
    List<GiftingRun> findAllScoped();

    @Query("""
        SELECT r FROM GiftingRun r
        WHERE r.id = :id AND r.brandId = ?#{@brandContext.brandId} AND r.deletedAt IS NULL
        """)
    Optional<GiftingRun> findScopedById(@Param("id") UUID id);
}
