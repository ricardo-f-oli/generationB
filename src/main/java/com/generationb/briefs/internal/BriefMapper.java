package com.generationb.briefs.internal;

import com.generationb.briefs.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BriefMapper {

    Brief toEntity(CreateBriefCommand command);

    BriefResponse toResponse(Brief brief);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromCommand(UpdateBriefCommand command, @MappingTarget Brief brief);
}
