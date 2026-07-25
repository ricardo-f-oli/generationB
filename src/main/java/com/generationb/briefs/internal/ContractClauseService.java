package com.generationb.briefs.internal;

import com.generationb.briefs.ContractClauseResponse;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing contract clause lookup and display order operations.
 */
@Service
@Transactional
@Audited
public class ContractClauseService {

    private final ContractClauseRepository contractClauseRepository;
    private final ContractClauseMapper contractClauseMapper;

    public ContractClauseService(ContractClauseRepository contractClauseRepository, 
                                 ContractClauseMapper contractClauseMapper) {
        this.contractClauseRepository = contractClauseRepository;
        this.contractClauseMapper = contractClauseMapper;
    }

    /**
     * Lists all active clauses for the current brand ordered by their display sequence.
     *
     * @return list of active ContractClauseResponse DTOs.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
    public List<ContractClauseResponse> listClauses() {
        return contractClauseRepository.findAllByBrandIdAndIsActiveTrueOrderByDisplayOrder().stream()
                .map(contractClauseMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Reorders contract clauses display order for brand-safety.
     *
     * @param orderedIds the list of clause IDs in their desired display order.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public void reorderClauses(List<UUID> orderedIds) {
        UUID currentBrandId = BrandContext.getCurrentBrandId();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            ContractClause clause = contractClauseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Clause not found: " + id));

            if (currentBrandId != null && !currentBrandId.equals(clause.getBrandId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to clause: " + id);
            }
            clause.setDisplayOrder(i + 1);
            contractClauseRepository.save(clause);
        }
    }
}
