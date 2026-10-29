package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreatorAudienceReportRepository extends JpaRepository<CreatorAudienceReport, UUID> {

    Optional<CreatorAudienceReport> findByCreatorIdAndPlatform(UUID creatorId, String platform);

    List<CreatorAudienceReport> findByCreatorIdOrderByPlatformAsc(UUID creatorId);

    @Modifying
    @Query("DELETE FROM CreatorAudienceReport r WHERE r.creatorId = :creatorId")
    int deleteByCreatorId(@Param("creatorId") UUID creatorId);
}
