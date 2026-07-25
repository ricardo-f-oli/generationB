package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CampaignCardMapper {
    CampaignCard toEntity(CreateCardCommand command);
    CampaignCardResponse toResponse(CampaignCard card);
    void updateEntityFromCommand(UpdateCardCommand command, @MappingTarget CampaignCard card);
}
