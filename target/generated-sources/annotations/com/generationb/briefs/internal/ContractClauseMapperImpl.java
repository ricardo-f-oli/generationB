package com.generationb.briefs.internal;

import com.generationb.briefs.ClauseType;
import com.generationb.briefs.ContractClauseResponse;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-25T16:04:06-0300",
    comments = "version: 1.6.3, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class ContractClauseMapperImpl implements ContractClauseMapper {

    @Override
    public ContractClauseResponse toResponse(ContractClause clause) {
        if ( clause == null ) {
            return null;
        }

        UUID id = null;
        UUID brandId = null;
        ClauseType clauseType = null;
        String content = null;
        int displayOrder = 0;

        id = clause.getId();
        brandId = clause.getBrandId();
        clauseType = clause.getClauseType();
        content = clause.getContent();
        displayOrder = clause.getDisplayOrder();

        boolean isActive = false;

        ContractClauseResponse contractClauseResponse = new ContractClauseResponse( id, brandId, clauseType, content, displayOrder, isActive );

        return contractClauseResponse;
    }
}
