package com.generationb.briefs.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BriefClauseRepository extends JpaRepository<BriefClause, UUID> {

    List<BriefClause> findByBriefIdOrderByDisplayOrderAsc(UUID briefId);

    boolean existsByBriefIdAndClauseId(UUID briefId, UUID clauseId);

    @Modifying
    @Query("DELETE FROM BriefClause bc WHERE bc.briefId = :briefId AND bc.clauseId = :clauseId")
    int detach(@Param("briefId") UUID briefId, @Param("clauseId") UUID clauseId);

    @Modifying
    @Query("DELETE FROM BriefClause bc WHERE bc.briefId = :briefId")
    int detachAll(@Param("briefId") UUID briefId);

    /** Next position, so an attach lands at the end rather than colliding on order 0. */
    @Query("SELECT COALESCE(MAX(bc.displayOrder), -1) + 1 FROM BriefClause bc "
         + "WHERE bc.briefId = :briefId")
    int nextDisplayOrder(@Param("briefId") UUID briefId);
}
