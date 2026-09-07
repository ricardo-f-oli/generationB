package com.generationb.outreach.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailThreadRepository extends JpaRepository<EmailThread, UUID> {

    // ---------------------------------------------------- retention (#37)

    /** Correspondence with a creator. Held while the relationship is live, then removed. */
    @Query("SELECT COUNT(t) FROM EmailThread t WHERE t.receivedAt < :before")
    int countReceivedBefore(@Param("before") java.time.Instant before);

    @Modifying
    @Query("DELETE FROM EmailThread t WHERE t.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") java.time.Instant before);

    @Query("SELECT MIN(t.receivedAt) FROM EmailThread t")
    java.time.Instant oldestReceivedAt();


    List<EmailThread> findByOutreachRecipientIdAndDeletedAtIsNullOrderByReceivedAtAsc(UUID outreachRecipientId);
}
