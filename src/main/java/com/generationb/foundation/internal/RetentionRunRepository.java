package com.generationb.foundation.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionRunRepository extends JpaRepository<RetentionRun, UUID> {

    /**
     * The most recent real pass for each dataset.
     *
     * <p>Previews are excluded: the GDPR screen is answering "when was this last actually
     * applied", and a preview applied nothing. {@code DISTINCT ON} is Postgres-specific but it is
     * the one that reads as the question being asked.
     */
    @Query(value = """
        SELECT DISTINCT ON (dataset) *
          FROM retention_runs
         WHERE dry_run = FALSE
         ORDER BY dataset, ran_at DESC
        """, nativeQuery = true)
    List<RetentionRun> findLatestPerDataset();

    @Query("SELECT r FROM RetentionRun r ORDER BY r.ranAt DESC")
    List<RetentionRun> findRecent(Pageable pageable);

    @Modifying
    @Query("DELETE FROM RetentionRun r WHERE r.ranAt < :before")
    int deleteRanBefore(@Param("before") Instant before);
}
