package com.generationb.briefs.internal;

import com.generationb.briefs.*;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BriefMapper {

    Brief toEntity(CreateBriefCommand command);

    BriefResponse toResponse(Brief brief);

    void updateEntityFromCommand(UpdateBriefCommand command, @MappingTarget Brief brief);
}
