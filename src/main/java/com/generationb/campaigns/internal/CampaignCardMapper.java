package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CampaignCardMapper {

    CampaignCard toEntity(CreateCardCommand command);

    CampaignCardResponse toResponse(CampaignCard card);

    /**
     * Q-E10: MapStruct's default is SET_TO_NULL, so a PATCH carrying only one field wiped
     * deliverables, fee, deadline, notes and approval status. IGNORE leaves absent fields alone.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromCommand(UpdateCardCommand command, @MappingTarget CampaignCard card);
}
