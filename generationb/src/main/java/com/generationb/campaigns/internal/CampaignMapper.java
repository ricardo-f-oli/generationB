package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CampaignMapper {
    Campaign toEntity(CreateCampaignCommand command);
    CampaignResponse toResponse(Campaign campaign);
}
