package com.generationb.outreach.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailThreadRepository extends JpaRepository<EmailThread, UUID> {

    List<EmailThread> findByOutreachRecipientIdAndDeletedAtIsNullOrderByReceivedAtAsc(UUID outreachRecipientId);
}
