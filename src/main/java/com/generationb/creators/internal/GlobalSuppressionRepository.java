package com.generationb.creators.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GlobalSuppressionRepository extends JpaRepository<GlobalSuppression, UUID> {

    boolean existsByCreatorId(UUID creatorId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByHandleIgnoreCase(String handle);

    @Query("SELECT s FROM GlobalSuppression s ORDER BY s.optedOutAt DESC")
    Page<GlobalSuppression> findAllOrdered(Pageable pageable);

    @Query("""
        SELECT COUNT(s) > 0 FROM GlobalSuppression s
        WHERE (:creatorId IS NOT NULL AND s.creatorId = :creatorId)
           OR (CAST(:email AS string) IS NOT NULL AND LOWER(s.email) = LOWER(CAST(:email AS string)))
        """)
    boolean isSuppressed(@Param("creatorId") UUID creatorId, @Param("email") String email);
}
