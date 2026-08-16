package com.generationb.marketing.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    Optional<WaitlistEntry> findByEmailIgnoreCase(String email);

    Optional<WaitlistEntry> findByConfirmToken(String confirmToken);

    @Query("SELECT w FROM WaitlistEntry w WHERE (CAST(:status AS string) IS NULL OR w.status = CAST(:status AS string)) ORDER BY w.createdAt DESC")
    Page<WaitlistEntry> findByStatus(@Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.status = :status")
    long countByStatus(@Param("status") String status);
}
