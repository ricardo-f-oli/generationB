package com.generationb.foundation.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstagramHashtagLookupRepository extends JpaRepository<InstagramHashtagLookup, UUID> {

    Optional<InstagramHashtagLookup> findByHashtag(String hashtag);

    /** How much of the 30-tag allowance is already committed inside the window. */
    @Query("SELECT COUNT(h) FROM InstagramHashtagLookup h WHERE h.firstUsedAt >= :windowStart")
    int countUsedSince(@Param("windowStart") Instant windowStart);

    @Query("SELECT h FROM InstagramHashtagLookup h WHERE h.firstUsedAt >= :windowStart "
         + "ORDER BY h.firstUsedAt ASC")
    List<InstagramHashtagLookup> usedSince(@Param("windowStart") Instant windowStart);

    /** When the oldest tag in the window ages out, which is when a slot frees up. */
    @Query("SELECT MIN(h.firstUsedAt) FROM InstagramHashtagLookup h WHERE h.firstUsedAt >= :windowStart")
    Instant oldestInWindow(@Param("windowStart") Instant windowStart);
}
