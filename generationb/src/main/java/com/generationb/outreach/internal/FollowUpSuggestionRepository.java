package com.generationb.outreach.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FollowUpSuggestionRepository extends JpaRepository<FollowUpSuggestion, UUID> {
}
