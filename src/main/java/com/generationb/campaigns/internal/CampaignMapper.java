package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CampaignMapper {
    Campaign toEntity(CreateCampaignCommand command);
    CampaignResponse toResponse(Campaign campaign);
}
