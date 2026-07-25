package com.generationb.briefs.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BriefShareRepository extends JpaRepository<BriefShare, UUID> {

    Optional<BriefShare> findByToken(String token);
}
