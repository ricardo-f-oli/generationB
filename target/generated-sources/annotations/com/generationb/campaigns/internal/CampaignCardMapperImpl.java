package com.generationb.campaigns.internal;

import com.generationb.campaigns.ApprovalStatus;
import com.generationb.campaigns.CampaignCardResponse;
import com.generationb.campaigns.CreateCardCommand;
import com.generationb.campaigns.PaymentStatus;
import com.generationb.campaigns.UpdateCardCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class CampaignCardMapperImpl implements CampaignCardMapper {

    @Override
    public CampaignCard toEntity(CreateCardCommand command) {
        if ( command == null ) {
            return null;
        }

        CampaignCard campaignCard = new CampaignCard();

        campaignCard.setCampaignId( command.campaignId() );
        campaignCard.setColumnId( command.columnId() );
        campaignCard.setCreatorId( command.creatorId() );
        campaignCard.setDeadline( command.deadline() );
        List<String> list = command.deliverables();
        if ( list != null ) {
            campaignCard.setDeliverables( new ArrayList<String>( list ) );
        }
        campaignCard.setFeeAmount( command.feeAmount() );
        campaignCard.setFeeCurrency( command.feeCurrency() );
        campaignCard.setNotes( command.notes() );

        return campaignCard;
    }

    @Override
    public CampaignCardResponse toResponse(CampaignCard card) {
        if ( card == null ) {
            return null;
        }

        UUID id = null;
        UUID boardId = null;
        UUID columnId = null;
        UUID brandId = null;
        UUID creatorId = null;
        UUID campaignId = null;
        List<String> deliverables = null;
        BigDecimal feeAmount = null;
        String feeCurrency = null;
        LocalDate deadline = null;
        PaymentStatus paymentStatus = null;
        List<String> contentDraftUrls = null;
        ApprovalStatus approvalStatus = null;
        String notes = null;

        id = card.getId();
        boardId = card.getBoardId();
        columnId = card.getColumnId();
        brandId = card.getBrandId();
        creatorId = card.getCreatorId();
        campaignId = card.getCampaignId();
        List<String> list = card.getDeliverables();
        if ( list != null ) {
            deliverables = new ArrayList<String>( list );
        }
        feeAmount = card.getFeeAmount();
        feeCurrency = card.getFeeCurrency();
        deadline = card.getDeadline();
        paymentStatus = card.getPaymentStatus();
        List<String> list1 = card.getContentDraftUrls();
        if ( list1 != null ) {
            contentDraftUrls = new ArrayList<String>( list1 );
        }
        approvalStatus = card.getApprovalStatus();
        notes = card.getNotes();

        CampaignCardResponse campaignCardResponse = new CampaignCardResponse( id, boardId, columnId, brandId, creatorId, campaignId, deliverables, feeAmount, feeCurrency, deadline, paymentStatus, contentDraftUrls, approvalStatus, notes );

        return campaignCardResponse;
    }

    @Override
    public void updateEntityFromCommand(UpdateCardCommand command, CampaignCard card) {
        if ( command == null ) {
            return;
        }

        card.setApprovalStatus( command.approvalStatus() );
        card.setColumnId( command.columnId() );
        if ( card.getContentDraftUrls() != null ) {
            List<String> list = command.contentDraftUrls();
            if ( list != null ) {
                card.getContentDraftUrls().clear();
                card.getContentDraftUrls().addAll( list );
            }
            else {
                card.setContentDraftUrls( null );
            }
        }
        else {
            List<String> list = command.contentDraftUrls();
            if ( list != null ) {
                card.setContentDraftUrls( new ArrayList<String>( list ) );
            }
        }
        card.setDeadline( command.deadline() );
        if ( card.getDeliverables() != null ) {
            List<String> list1 = command.deliverables();
            if ( list1 != null ) {
                card.getDeliverables().clear();
                card.getDeliverables().addAll( list1 );
            }
            else {
                card.setDeliverables( null );
            }
        }
        else {
            List<String> list1 = command.deliverables();
            if ( list1 != null ) {
                card.setDeliverables( new ArrayList<String>( list1 ) );
            }
        }
        card.setFeeAmount( command.feeAmount() );
        card.setFeeCurrency( command.feeCurrency() );
        card.setNotes( command.notes() );
    }
}
