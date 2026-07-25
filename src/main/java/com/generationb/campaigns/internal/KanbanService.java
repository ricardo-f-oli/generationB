package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.shared.CardMovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing Kanban boards, columns, and cards operations.
 */
@Service
@Transactional
@Audited
public class KanbanService {

    private final KanbanBoardRepository kanbanBoardRepository;
    private final KanbanColumnRepository kanbanColumnRepository;
    private final CampaignCardRepository campaignCardRepository;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final CampaignCardMapper campaignCardMapper;
    private final ApplicationEventPublisher eventPublisher;

    public KanbanService(KanbanBoardRepository kanbanBoardRepository,
                         KanbanColumnRepository kanbanColumnRepository,
                         CampaignCardRepository campaignCardRepository,
                         KanbanBoardMapper kanbanBoardMapper,
                         CampaignCardMapper campaignCardMapper,
                         ApplicationEventPublisher eventPublisher) {
        this.kanbanBoardRepository = kanbanBoardRepository;
        this.kanbanColumnRepository = kanbanColumnRepository;
        this.campaignCardRepository = campaignCardRepository;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.campaignCardMapper = campaignCardMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new Kanban board for a campaign and populates it with default stages.
     *
     * @param campaignId target campaign ID.
     * @param command board properties.
     * @return BoardResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BoardResponse createBoard(UUID campaignId, CreateBoardCommand command) {
        KanbanBoard board = kanbanBoardMapper.toEntity(command);
        board.setCampaignId(campaignId);
        KanbanBoard savedBoard = kanbanBoardRepository.save(board);

        // Auto-initialize standard board workflow stages
        String[] defaultStages = {"Briefing", "In Progress", "Awaiting Approval", "Published"};
        boolean[] approvals = {false, false, true, false};
        boolean[] triggers = {false, false, true, false};
        for (int i = 0; i < defaultStages.length; i++) {
            KanbanColumn col = new KanbanColumn();
            col.setBoardId(savedBoard.getId());
            col.setName(defaultStages[i]);
            col.setDisplayOrder(i + 1);
            col.setRequiresDirectorApproval(approvals[i]);
            col.setTriggersEmail(triggers[i]);
            col.setBrandId(savedBoard.getBrandId());
            kanbanColumnRepository.save(col);
        }

        return kanbanBoardMapper.toResponse(savedBoard);
    }

    /**
     * Gets a complete Kanban board details including columns and card details.
     *
     * @param boardId board UUID.
     * @return BoardWithCardsResponse DTO.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE', 'VIEW_ONLY')")
    public BoardWithCardsResponse getBoardWithCards(UUID boardId) {
        KanbanBoard board = kanbanBoardRepository.findByIdAndBrandId(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));

        List<KanbanColumn> columns = kanbanColumnRepository.findAllByBoardIdAndBrandIdOrderByDisplayOrder(boardId);
        List<CampaignCard> cards = campaignCardRepository.findAllByBoardIdAndBrandId(boardId);

        Map<UUID, List<CampaignCardResponse>> cardsByColumn = cards.stream()
                .map(campaignCardMapper::toResponse)
                .collect(Collectors.groupingBy(CampaignCardResponse::columnId));

        List<BoardWithCardsResponse.ColumnWithCardsResponse> cols = columns.stream()
                .map(c -> new BoardWithCardsResponse.ColumnWithCardsResponse(
                        c.getId(),
                        c.getName(),
                        c.getDisplayOrder(),
                        c.isRequiresDirectorApproval(),
                        c.isRequiresClientApproval(),
                        c.isTriggersEmail(),
                        c.getTriggerTemplateId(),
                        cardsByColumn.getOrDefault(c.getId(), Collections.emptyList())
                ))
                .collect(Collectors.toList());

        return new BoardWithCardsResponse(
                board.getId(),
                board.getCampaignId(),
                board.getBrandId(),
                board.getName(),
                cols
        );
    }

    /**
     * Creates a campaign card representing a creator workflow on a board.
     *
     * @param boardId target board ID.
     * @param command card values.
     * @return CampaignCardResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse createCard(UUID boardId, CreateCardCommand command) {
        // Validate target column exists on this board
        KanbanColumn column = kanbanColumnRepository.findById(command.columnId())
                .orElseThrow(() -> new IllegalArgumentException("Column not found"));
        if (!column.getBoardId().equals(boardId)) {
            throw new IllegalArgumentException("Column does not belong to the target board");
        }

        CampaignCard card = campaignCardMapper.toEntity(command);
        card.setBoardId(boardId);
        card.setPaymentStatus(PaymentStatus.UNPAID);
        card.setApprovalStatus(ApprovalStatus.PENDING);

        CampaignCard saved = campaignCardRepository.save(card);
        return campaignCardMapper.toResponse(saved);
    }

    /**
     * Updates card details.
     *
     * @param cardId card UUID.
     * @param command properties to update.
     * @return CampaignCardResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse updateCard(UUID cardId, UpdateCardCommand command) {
        CampaignCard card = campaignCardRepository.findByIdAndBrandId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));

        if (command.columnId() != null) {
            KanbanColumn column = kanbanColumnRepository.findById(command.columnId())
                    .orElseThrow(() -> new IllegalArgumentException("Column not found"));
            if (!column.getBoardId().equals(card.getBoardId())) {
                throw new IllegalArgumentException("Column does not belong to the board");
            }
            card.setColumnId(command.columnId());
        }

        campaignCardMapper.updateEntityFromCommand(command, card);
        CampaignCard saved = campaignCardRepository.save(card);
        return campaignCardMapper.toResponse(saved);
    }

    /**
     * Moves a card to another column and fires cross-module event.
     *
     * @param cardId card UUID.
     * @param targetColumnId target column UUID.
     * @return CampaignCardResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse moveCard(UUID cardId, UUID targetColumnId) {
        CampaignCard card = campaignCardRepository.findByIdAndBrandId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
        UUID fromColumnId = card.getColumnId();

        KanbanColumn targetColumn = kanbanColumnRepository.findById(targetColumnId)
                .orElseThrow(() -> new IllegalArgumentException("Target column not found"));

        if (!targetColumn.getBoardId().equals(card.getBoardId())) {
            throw new IllegalArgumentException("Target column does not belong to the card's board");
        }

        card.setColumnId(targetColumnId);
        CampaignCard saved = campaignCardRepository.save(card);

        // Publish modular application event
        eventPublisher.publishEvent(new CardMovedEvent(
                cardId, fromColumnId, targetColumnId, card.getBrandId(), Instant.now()
        ));

        return campaignCardMapper.toResponse(saved);
    }

    /**
     * Updates payment status on a campaign card.
     *
     * @param cardId card UUID.
     * @param status payment status value.
     * @return CampaignCardResponse DTO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER')")
    public CampaignCardResponse updatePaymentStatus(UUID cardId, PaymentStatus status) {
        CampaignCard card = campaignCardRepository.findByIdAndBrandId(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
        card.setPaymentStatus(status);
        CampaignCard saved = campaignCardRepository.save(card);
        return campaignCardMapper.toResponse(saved);
    }

    /**
     * Bulk moves a list of cards to a new column.
     *
     * @param cardIds list of card IDs.
     * @param targetColumnId target column ID.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public void bulkMoveCards(List<UUID> cardIds, UUID targetColumnId) {
        KanbanColumn targetColumn = kanbanColumnRepository.findById(targetColumnId)
                .orElseThrow(() -> new IllegalArgumentException("Target column not found"));

        for (UUID cardId : cardIds) {
            CampaignCard card = campaignCardRepository.findByIdAndBrandId(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));

            if (!targetColumn.getBoardId().equals(card.getBoardId())) {
                throw new IllegalArgumentException("Card and column board mismatch: " + cardId);
            }

            UUID fromColumnId = card.getColumnId();
            card.setColumnId(targetColumnId);
            campaignCardRepository.save(card);

            eventPublisher.publishEvent(new CardMovedEvent(
                    cardId, fromColumnId, targetColumnId, card.getBrandId(), Instant.now()
            ));
        }
    }
}
