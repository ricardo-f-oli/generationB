package com.generationb.foundation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query("""
        SELECT COUNT(a) FROM LoginAttempt a
        WHERE LOWER(a.identifier) = LOWER(:identifier)
          AND a.successful = false
          AND a.attemptedAt > :since
        """)
    long countRecentFailuresForIdentifier(@Param("identifier") String identifier,
                                          @Param("since") Instant since);

    @Query("""
        SELECT COUNT(a) FROM LoginAttempt a
        WHERE a.ipAddress = :ip
          AND a.successful = false
          AND a.attemptedAt > :since
        """)
    long countRecentFailuresForIp(@Param("ip") String ip, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.attemptedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
