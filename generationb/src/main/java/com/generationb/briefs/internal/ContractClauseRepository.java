package com.generationb.briefs.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ContractClauseRepository extends JpaRepository<ContractClause, UUID> {

    @Query("SELECT c FROM ContractClause c WHERE c.brandId = ?#{@brandContext.brandId} AND c.isActive = true ORDER BY c.displayOrder ASC")
    List<ContractClause> findAllByBrandIdAndIsActiveTrueOrderByDisplayOrder();
}
