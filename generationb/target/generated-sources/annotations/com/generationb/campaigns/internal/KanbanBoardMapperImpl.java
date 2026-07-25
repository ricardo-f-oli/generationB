package com.generationb.campaigns.internal;

import com.generationb.campaigns.BoardResponse;
import com.generationb.campaigns.CreateBoardCommand;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-25T15:30:27-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.5 (Oracle Corporation)"
)
@Component
public class KanbanBoardMapperImpl implements KanbanBoardMapper {

    @Override
    public KanbanBoard toEntity(CreateBoardCommand command) {
        if ( command == null ) {
            return null;
        }

        KanbanBoard kanbanBoard = new KanbanBoard();

        kanbanBoard.setName( command.name() );

        return kanbanBoard;
    }

    @Override
    public BoardResponse toResponse(KanbanBoard board) {
        if ( board == null ) {
            return null;
        }

        UUID id = null;
        UUID campaignId = null;
        UUID brandId = null;
        String name = null;

        id = board.getId();
        campaignId = board.getCampaignId();
        brandId = board.getBrandId();
        name = board.getName();

        BoardResponse boardResponse = new BoardResponse( id, campaignId, brandId, name );

        return boardResponse;
    }
}
