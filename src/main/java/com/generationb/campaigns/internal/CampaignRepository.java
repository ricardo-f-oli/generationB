package com.generationb.campaigns.internal;

import com.generationb.campaigns.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    @Query("SELECT c FROM Campaign c WHERE c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL")
    Page<Campaign> findAllByBrandIdAndDeletedAtIsNull(Pageable pageable);

    /** Q-J16: list endpoints support filtering. */
    @Query("""
        SELECT c FROM Campaign c
        WHERE c.brandId = ?#{@brandContext.brandId}
          AND c.deletedAt IS NULL
          AND (:status IS NULL OR c.status = :status)
        """)
    Page<Campaign> findAllFiltered(@Param("status") CampaignStatus status, Pageable pageable);

    @Query("""
        SELECT c FROM Campaign c
        WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<Campaign> findByIdAndBrandId(@Param("id") UUID id);
}
