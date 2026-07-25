package com.generationb.campaigns.internal;

import com.generationb.campaigns.CampaignResponse;
import com.generationb.campaigns.CampaignStatus;
import com.generationb.campaigns.CampaignType;
import com.generationb.campaigns.CreateCampaignCommand;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-25T15:30:27-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.5 (Oracle Corporation)"
)
@Component
public class CampaignMapperImpl implements CampaignMapper {

    @Override
    public Campaign toEntity(CreateCampaignCommand command) {
        if ( command == null ) {
            return null;
        }

        Campaign campaign = new Campaign();

        campaign.setName( command.name() );
        campaign.setCampaignType( command.campaignType() );
        campaign.setStartDate( command.startDate() );
        campaign.setEndDate( command.endDate() );

        return campaign;
    }

    @Override
    public CampaignResponse toResponse(Campaign campaign) {
        if ( campaign == null ) {
            return null;
        }

        UUID id = null;
        UUID brandId = null;
        String name = null;
        CampaignType campaignType = null;
        CampaignStatus status = null;
        Instant startDate = null;
        Instant endDate = null;
        UUID createdBy = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        id = campaign.getId();
        brandId = campaign.getBrandId();
        name = campaign.getName();
        campaignType = campaign.getCampaignType();
        status = campaign.getStatus();
        startDate = campaign.getStartDate();
        endDate = campaign.getEndDate();
        createdBy = campaign.getCreatedBy();
        createdAt = campaign.getCreatedAt();
        updatedAt = campaign.getUpdatedAt();

        CampaignResponse campaignResponse = new CampaignResponse( id, brandId, name, campaignType, status, startDate, endDate, createdBy, createdAt, updatedAt );

        return campaignResponse;
    }
}
