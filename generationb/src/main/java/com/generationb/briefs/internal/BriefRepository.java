package com.generationb.briefs.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface BriefRepository extends JpaRepository<Brief, UUID> {

    @Query("SELECT b FROM Brief b WHERE b.brandId = ?#{@brandContext.brandId} AND b.deletedAt IS NULL")
    Page<Brief> findAllByBrandIdAndDeletedAtIsNull(Pageable pageable);

    @Query("SELECT b FROM Brief b WHERE b.id = :id AND b.brandId = ?#{@brandContext.brandId} AND b.deletedAt IS NULL")
    Optional<Brief> findByIdAndBrandId(@Param("id") UUID id);
}
