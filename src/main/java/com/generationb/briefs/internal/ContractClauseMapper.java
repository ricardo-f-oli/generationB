package com.generationb.briefs.internal;

import com.generationb.briefs.ContractClauseResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ContractClauseMapper {

    ContractClauseResponse toResponse(ContractClause clause);
}
