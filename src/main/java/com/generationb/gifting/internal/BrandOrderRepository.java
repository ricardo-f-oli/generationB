package com.generationb.gifting.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandOrderRepository extends JpaRepository<BrandOrder, UUID> {

    @Query("""
        SELECT o FROM BrandOrder o
        WHERE o.brandId = ?#{@brandContext.brandId} AND o.deletedAt IS NULL
        ORDER BY o.createdAt DESC
        """)
    List<BrandOrder> findAllScoped();

    @Query("""
        SELECT o FROM BrandOrder o
        WHERE o.id = :id AND o.brandId = ?#{@brandContext.brandId} AND o.deletedAt IS NULL
        """)
    Optional<BrandOrder> findScopedById(@Param("id") UUID id);

    /** The brand contact has no login, so this one is deliberately not brand-scoped. */
    @Query("SELECT o FROM BrandOrder o WHERE o.confirmToken = :token AND o.deletedAt IS NULL")
    Optional<BrandOrder> findByConfirmToken(@Param("token") String token);
}
