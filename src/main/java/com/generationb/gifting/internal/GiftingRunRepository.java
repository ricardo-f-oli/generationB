package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GiftingRunRepository extends JpaRepository<GiftingRun, UUID> {
}
