package com.generationb.briefs.internal;

import com.generationb.briefs.ContractClauseResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ContractClauseMapper {
    ContractClauseResponse toResponse(ContractClause clause);
}
