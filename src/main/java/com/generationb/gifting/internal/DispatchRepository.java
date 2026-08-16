package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {

    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.brandId = :brandId AND d.deletedAt IS NULL
        ORDER BY d.createdAt DESC
        """)
    List<Dispatch> findAllForBrand(@Param("brandId") UUID brandId);

    @Query("SELECT d FROM Dispatch d WHERE d.id = :id AND d.brandId = :brandId AND d.deletedAt IS NULL")
    Optional<Dispatch> findScopedById(@Param("id") UUID id, @Param("brandId") UUID brandId);

    @Query("SELECT d FROM Dispatch d WHERE d.giftingRunId = :runId AND d.deletedAt IS NULL")
    List<Dispatch> findByGiftingRunId(@Param("runId") UUID runId);

    /** Requirement #46: delivered parcels whose content deadline is approaching. */
    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.status = 'DELIVERED'
          AND d.deletedAt IS NULL
          AND d.contentDeadline IS NOT NULL
          AND d.contentDeadline BETWEEN :from AND :to
        """)
    List<Dispatch> findDeliveredWithDeadlineBetween(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);
}
