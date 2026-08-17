package com.generationb.creators.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreatorFollowerSnapshotRepository extends JpaRepository<CreatorFollowerSnapshot, UUID> {

    interface GrowthProjection {
        UUID getCreatorId();
        Integer getStartFollowers();
        Integer getEndFollowers();
    }

    boolean existsByCreatorIdAndCapturedOn(UUID creatorId, LocalDate capturedOn);

    /**
     * Earliest and latest snapshot inside the window, per creator. Requirement #49.
     *
     * <p>A creator with only one snapshot is excluded rather than returned with a delta of zero.
     * Growth is the difference between two points in time; with one point there is no difference
     * to report, and "0" would read as "they gained nobody" rather than "we cannot tell yet".
     */
    @Query(value = """
        SELECT creator_id AS creatorId,
               (ARRAY_AGG(followers_count ORDER BY captured_on ASC))[1]  AS startFollowers,
               (ARRAY_AGG(followers_count ORDER BY captured_on DESC))[1] AS endFollowers
        FROM creator_follower_snapshots
        WHERE creator_id = ANY(CAST(:creatorIds AS uuid[]))
          AND (CAST(:from AS date) IS NULL OR captured_on >= CAST(:from AS date))
          AND (CAST(:to   AS date) IS NULL OR captured_on <= CAST(:to AS date))
        GROUP BY creator_id
        HAVING COUNT(DISTINCT captured_on) >= 2
        """, nativeQuery = true)
    List<GrowthProjection> growthBetween(@Param("creatorIds") String creatorIds,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
