package com.generationb.briefs.internal;

import com.generationb.briefs.BriefResponse;
import com.generationb.briefs.BriefStatus;
import com.generationb.briefs.CreateBriefCommand;
import com.generationb.briefs.ToneOfVoice;
import com.generationb.briefs.UpdateBriefCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-25T16:04:06-0300",
    comments = "version: 1.6.3, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class BriefMapperImpl implements BriefMapper {

    @Override
    public Brief toEntity(CreateBriefCommand command) {
        if ( command == null ) {
            return null;
        }

        Brief brief = new Brief();

        brief.setAdditionalNotes( command.additionalNotes() );
        brief.setBudgetMax( command.budgetMax() );
        brief.setBudgetMin( command.budgetMin() );
        brief.setCampaignGoal( command.campaignGoal() );
        brief.setCampaignName( command.campaignName() );
        List<String> list = command.deliverables();
        if ( list != null ) {
            brief.setDeliverables( new ArrayList<String>( list ) );
        }
        brief.setKeyMessages( command.keyMessages() );
        brief.setTimelineEnd( command.timelineEnd() );
        brief.setTimelineStart( command.timelineStart() );
        brief.setToneOfVoice( command.toneOfVoice() );

        return brief;
    }

    @Override
    public BriefResponse toResponse(Brief brief) {
        if ( brief == null ) {
            return null;
        }

        UUID id = null;
        UUID brandId = null;
        String campaignName = null;
        String campaignGoal = null;
        String keyMessages = null;
        List<String> deliverables = null;
        BigDecimal budgetMin = null;
        BigDecimal budgetMax = null;
        Instant timelineStart = null;
        Instant timelineEnd = null;
        ToneOfVoice toneOfVoice = null;
        String additionalNotes = null;
        String aiGeneratedContent = null;
        BriefStatus status = null;
        UUID createdBy = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        id = brief.getId();
        brandId = brief.getBrandId();
        campaignName = brief.getCampaignName();
        campaignGoal = brief.getCampaignGoal();
        keyMessages = brief.getKeyMessages();
        List<String> list = brief.getDeliverables();
        if ( list != null ) {
            deliverables = new ArrayList<String>( list );
        }
        budgetMin = brief.getBudgetMin();
        budgetMax = brief.getBudgetMax();
        timelineStart = brief.getTimelineStart();
        timelineEnd = brief.getTimelineEnd();
        toneOfVoice = brief.getToneOfVoice();
        additionalNotes = brief.getAdditionalNotes();
        aiGeneratedContent = brief.getAiGeneratedContent();
        status = brief.getStatus();
        createdBy = brief.getCreatedBy();
        createdAt = brief.getCreatedAt();
        updatedAt = brief.getUpdatedAt();

        BriefResponse briefResponse = new BriefResponse( id, brandId, campaignName, campaignGoal, keyMessages, deliverables, budgetMin, budgetMax, timelineStart, timelineEnd, toneOfVoice, additionalNotes, aiGeneratedContent, status, createdBy, createdAt, updatedAt );

        return briefResponse;
    }

    @Override
    public void updateEntityFromCommand(UpdateBriefCommand command, Brief brief) {
        if ( command == null ) {
            return;
        }

        brief.setAdditionalNotes( command.additionalNotes() );
        brief.setBudgetMax( command.budgetMax() );
        brief.setBudgetMin( command.budgetMin() );
        brief.setCampaignGoal( command.campaignGoal() );
        brief.setCampaignName( command.campaignName() );
        if ( brief.getDeliverables() != null ) {
            List<String> list = command.deliverables();
            if ( list != null ) {
                brief.getDeliverables().clear();
                brief.getDeliverables().addAll( list );
            }
            else {
                brief.setDeliverables( null );
            }
        }
        else {
            List<String> list = command.deliverables();
            if ( list != null ) {
                brief.setDeliverables( new ArrayList<String>( list ) );
            }
        }
        brief.setKeyMessages( command.keyMessages() );
        brief.setTimelineEnd( command.timelineEnd() );
        brief.setTimelineStart( command.timelineStart() );
        brief.setToneOfVoice( command.toneOfVoice() );
    }
}
