package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import com.generationb.shared.CreatorLookupPort;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.shared.CardMovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kanban boards, columns and cards.
 *
 * <p>Rewritten against the answered review: stages come from a per-brand template rather than a
 * hardcoded array (#4/#7), cards are ordered so drag-and-drop works (#6/Q-E20), approval gates are
 * enforced (Q-E18), and every cross-entity lookup is brand-scoped (Q-C7).
 */
@Slf4j
@Service
@Transactional
@Audited
@RequiredArgsConstructor
public class KanbanService {

    /** Q-J14: a bulk move must not be able to take out the database. */
    private static final int MAX_BULK_CARDS = 200;

    private final KanbanBoardRepository boardRepository;
    private final KanbanColumnRepository columnRepository;
    private final CampaignCardRepository cardRepository;
    private final CampaignRepository campaignRepository;
    private final CardCommentRepository commentRepository;
    private final BoardTemplateRepository templateRepository;
    private final BoardTemplateColumnRepository templateColumnRepository;
    private final SavedViewRepository savedViewRepository;
    private final CreatorLookupPort creatorLookup;
    private final KanbanBoardMapper boardMapper;
    private final CampaignCardMapper cardMapper;
    private final ApplicationEventPublisher eventPublisher;

    // --------------------------------------------------------------- boards

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BoardResponse createBoard(UUID campaignId, CreateBoardCommand command) {
        // Q-C7: the campaign must exist and belong to this brand.
        Campaign campaign = campaignRepository.findByIdAndBrandId(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign"));

        // Idempotent: a campaign has one board, so a repeat call returns the existing one
        // rather than silently creating a duplicate.
        KanbanBoard existing = boardRepository.findByCampaignIdAndBrandId(campaignId).orElse(null);
        if (existing != null) {
            return boardMapper.toResponse(existing);
        }

        KanbanBoard board = boardMapper.toEntity(command);
        board.setCampaignId(campaignId);
        KanbanBoard savedBoard = boardRepository.save(board);

        instantiateColumns(savedBoard, campaign.getCampaignType());
        return boardMapper.toResponse(savedBoard);
    }

    /** Requirements #4/#7: stages come from the brand's template for this campaign type. */
    private void instantiateColumns(KanbanBoard board, CampaignType campaignType) {
        List<BoardTemplateColumn> templateColumns = templateRepository
                .findDefaultFor(campaignType == null ? CampaignType.SEEDING.name() : campaignType.name())
                .map(t -> templateColumnRepository.findByTemplateIdOrderByDisplayOrder(t.getId()))
                .orElseGet(List::of);

        if (templateColumns.isEmpty()) {
            log.info("No board template for brand {} / type {}; using the standard stages",
                    board.getBrandId(), campaignType);
            templateColumns = defaultColumns();
        }

        for (BoardTemplateColumn source : templateColumns) {
            KanbanColumn column = new KanbanColumn();
            column.setBoardId(board.getId());
            column.setBrandId(board.getBrandId());
            column.setName(source.getName());
            column.setDisplayOrder(source.getDisplayOrder());
            column.setRequiresDirectorApproval(source.isRequiresDirectorApproval());
            column.setRequiresClientApproval(source.isRequiresClientApproval());
            column.setTriggersEmail(source.isTriggersEmail());
            column.setTriggerTemplateId(source.getTriggerTemplateId());
            columnRepository.save(column);
        }
    }

    /** Q-E19: the frontend's 7-stage set is the correct one. */
    private List<BoardTemplateColumn> defaultColumns() {
        String[] names = {"Target List", "Brief Sent", "Content Draft", "Brand Review",
                          "Approved", "Live", "Reporting"};
        boolean[] directorApproval = {false, false, false, false, true, false, false};
        boolean[] clientApproval = {false, false, false, true, false, false, false};
        boolean[] triggersEmail = {false, true, false, false, false, false, false};

        List<BoardTemplateColumn> columns = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            BoardTemplateColumn column = new BoardTemplateColumn();
            column.setName(names[i]);
            column.setDisplayOrder(i + 1);
            column.setRequiresDirectorApproval(directorApproval[i]);
            column.setRequiresClientApproval(clientApproval[i]);
            column.setTriggersEmail(triggersEmail[i]);
            columns.add(column);
        }
        return columns;
    }

    /** One board per campaign, created on first access from the brand's template. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BoardWithCardsResponse getOrCreateBoardForCampaign(UUID campaignId, String filter) {
        Campaign campaign = campaignRepository.findByIdAndBrandId(campaignId)
                .orElseThrow(() -> ApiException.notFound("Campaign"));

        KanbanBoard board = boardRepository.findByCampaignIdAndBrandId(campaignId)
                .orElseGet(() -> {
                    KanbanBoard created = new KanbanBoard();
                    created.setCampaignId(campaignId);
                    created.setName(campaign.getName() + " board");
                    KanbanBoard saved = boardRepository.save(created);
                    instantiateColumns(saved, campaign.getCampaignType());
                    return saved;
                });

        return (filter == null || filter.isBlank())
                ? getBoardWithCards(board.getId())
                : getFilteredBoard(board.getId(), filter);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BoardWithCardsResponse getBoardWithCards(UUID boardId) {
        return buildBoard(boardId, cardRepository.findAllByBoardIdAndBrandId(boardId));
    }

    /** Requirement #9: the same board, filtered by a saved view's criteria. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public BoardWithCardsResponse getFilteredBoard(UUID boardId, String filter) {
        UUID assignee = null;
        Boolean blocked = null;
        ApprovalStatus approvalStatus = null;
        LocalDate dueBefore = null;

        if (filter != null) {
            switch (filter) {
                case "my-cards" -> assignee = BrandContext.getCurrentUserId();
                case "blocked" -> blocked = true;
                case "awaiting-approval" -> approvalStatus = ApprovalStatus.PENDING;
                case "due-this-week" -> dueBefore = LocalDate.now().plusDays(7);
                default -> { /* unknown filter: fall through to everything */ }
            }
        }

        return buildBoard(boardId,
                cardRepository.findFiltered(boardId, assignee, blocked, approvalStatus, dueBefore));
    }

    private BoardWithCardsResponse buildBoard(UUID boardId, List<CampaignCard> cards) {
        KanbanBoard board = boardRepository.findByIdAndBrandId(boardId)
                .orElseThrow(() -> ApiException.notFound("Board"));

        List<KanbanColumn> columns =
                columnRepository.findAllByBoardIdAndBrandIdOrderByDisplayOrder(boardId);

        // Q-G2: resolve every creator handle in one call rather than one query per card.
        Map<UUID, String> handles = creatorLookup
                .findContacts(cards.stream().map(CampaignCard::getCreatorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CreatorLookupPort.CreatorContact::creatorId,
                        CreatorLookupPort.CreatorContact::handle, (a, b) -> a));

        Map<UUID, List<CampaignCardResponse>> cardsByColumn = cards.stream()
                .map(card -> withHandle(cardMapper.toResponse(card), handles.get(card.getCreatorId())))
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
                        cardsByColumn.getOrDefault(c.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        return new BoardWithCardsResponse(
                board.getId(), board.getCampaignId(), board.getBrandId(), board.getName(), cols);
    }

    // -------------------------------------------------------------- columns

    /** Requirement #4: admins define the stages. None of this existed before. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public BoardWithCardsResponse.ColumnWithCardsResponse addColumn(UUID boardId, CreateColumnCommand command) {
        KanbanBoard board = boardRepository.findByIdAndBrandId(boardId)
                .orElseThrow(() -> ApiException.notFound("Board"));

        List<KanbanColumn> existing =
                columnRepository.findAllByBoardIdAndBrandIdOrderByDisplayOrder(boardId);

        KanbanColumn column = new KanbanColumn();
        column.setBoardId(board.getId());
        column.setBrandId(board.getBrandId());
        column.setName(command.name());
        column.setDisplayOrder(existing.size() + 1);
        column.setRequiresDirectorApproval(command.requiresDirectorApproval());
        column.setRequiresClientApproval(command.requiresClientApproval());
        column.setTriggersEmail(command.triggersEmail());
        column.setTriggerTemplateId(command.triggerTemplateId());

        return toColumnResponse(columnRepository.save(column));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public BoardWithCardsResponse.ColumnWithCardsResponse updateColumn(UUID columnId, CreateColumnCommand command) {
        KanbanColumn column = columnRepository.findScopedById(columnId)
                .orElseThrow(() -> ApiException.notFound("Column"));

        if (command.name() != null && !command.name().isBlank()) {
            column.setName(command.name());
        }
        column.setRequiresDirectorApproval(command.requiresDirectorApproval());
        column.setRequiresClientApproval(command.requiresClientApproval());
        column.setTriggersEmail(command.triggersEmail());
        column.setTriggerTemplateId(command.triggerTemplateId());

        return toColumnResponse(columnRepository.save(column));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public void reorderColumns(UUID boardId, List<UUID> orderedColumnIds) {
        boardRepository.findByIdAndBrandId(boardId)
                .orElseThrow(() -> ApiException.notFound("Board"));

        for (int i = 0; i < orderedColumnIds.size(); i++) {
            KanbanColumn column = columnRepository.findScopedById(orderedColumnIds.get(i))
                    .orElseThrow(() -> ApiException.notFound("Column"));
            if (!column.getBoardId().equals(boardId)) {
                throw ApiException.badRequest("Column does not belong to this board");
            }
            column.setDisplayOrder(i + 1);
            columnRepository.save(column);
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public void deleteColumn(UUID columnId) {
        KanbanColumn column = columnRepository.findScopedById(columnId)
                .orElseThrow(() -> ApiException.notFound("Column"));
        if (!cardRepository.findByColumn(columnId, PageRequest.of(0, 1)).isEmpty()) {
            throw ApiException.conflict("Move the cards out of this stage before deleting it");
        }
        column.setDeletedAt(Instant.now());
        columnRepository.save(column);
    }

    // ---------------------------------------------------------------- cards

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse createCard(UUID boardId, CreateCardCommand command) {
        KanbanBoard board = boardRepository.findByIdAndBrandId(boardId)
                .orElseThrow(() -> ApiException.notFound("Board"));
        KanbanColumn column = columnRepository.findScopedById(command.columnId())
                .orElseThrow(() -> ApiException.notFound("Column"));
        if (!column.getBoardId().equals(boardId)) {
            throw ApiException.badRequest("That stage does not belong to this board");
        }
        // Q-J13: the card's campaign must match the board's campaign.
        if (command.campaignId() != null && !command.campaignId().equals(board.getCampaignId())) {
            throw ApiException.badRequest("Card campaign does not match the board's campaign");
        }

        CampaignCard card = cardMapper.toEntity(command);
        card.setBoardId(boardId);
        card.setCampaignId(board.getCampaignId());
        card.setPaymentStatus(PaymentStatus.UNPAID);
        card.setApprovalStatus(ApprovalStatus.PENDING);
        card.setPosition(cardRepository.findMaxPosition(command.columnId()) + 1);

        CampaignCard persisted = cardRepository.save(card);
        return withResolvedHandle(cardMapper.toResponse(persisted), persisted.getCreatorId());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse updateCard(UUID cardId, UpdateCardCommand command) {
        CampaignCard card = requireCard(cardId);

        if (command.columnId() != null) {
            KanbanColumn column = columnRepository.findScopedById(command.columnId())
                    .orElseThrow(() -> ApiException.notFound("Column"));
            if (!column.getBoardId().equals(card.getBoardId())) {
                throw ApiException.badRequest("That stage does not belong to this board");
            }
            card.setColumnId(command.columnId());
        }

        cardMapper.updateEntityFromCommand(command, card);
        CampaignCard persisted = cardRepository.save(card);
        return withResolvedHandle(cardMapper.toResponse(persisted), persisted.getCreatorId());
    }

    /**
     * Requirement #6. Q-E18: the approval gate is enforced — a card cannot enter a stage that
     * requires director sign-off until it has been approved.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public CampaignCardResponse moveCard(UUID cardId, UUID targetColumnId, Integer targetPosition) {
        CampaignCard card = requireCard(cardId);
        UUID fromColumnId = card.getColumnId();

        KanbanColumn targetColumn = columnRepository.findScopedById(targetColumnId)
                .orElseThrow(() -> ApiException.notFound("Target column"));
        if (!targetColumn.getBoardId().equals(card.getBoardId())) {
            throw ApiException.badRequest("Target stage does not belong to this card's board");
        }

        enforceApprovalGate(card, targetColumn);

        card.setColumnId(targetColumnId);
        card.setPosition(targetPosition != null
                ? targetPosition
                : cardRepository.findMaxPosition(targetColumnId) + 1);
        CampaignCard saved = cardRepository.save(card);

        if (targetPosition != null) {
            resequence(targetColumnId, cardId, targetPosition);
        }

        eventPublisher.publishEvent(new CardMovedEvent(
                cardId, fromColumnId, targetColumnId, card.getBrandId(), Instant.now()));

        return withResolvedHandle(cardMapper.toResponse(saved), saved.getCreatorId());
    }

    private void enforceApprovalGate(CampaignCard card, KanbanColumn targetColumn) {
        if (targetColumn.isRequiresDirectorApproval()
                && card.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.unprocessable(
                    "\"" + targetColumn.getName() + "\" requires director sign-off. "
                            + "Approve the card before moving it here.");
        }
        if (targetColumn.isRequiresClientApproval()
                && card.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw ApiException.unprocessable(
                    "This card was rejected in client review and cannot move forward.");
        }
    }

    /** Keeps positions dense and unique after a drag-and-drop drop. */
    private void resequence(UUID columnId, UUID movedCardId, int targetPosition) {
        List<CampaignCard> others = cardRepository
                .findByColumn(columnId, PageRequest.of(0, 500))
                .getContent().stream()
                .filter(c -> !c.getId().equals(movedCardId))
                .collect(Collectors.toCollection(ArrayList::new));

        int index = 0;
        for (CampaignCard other : others) {
            if (index == targetPosition) {
                index++;
            }
            other.setPosition(index++);
        }
        cardRepository.saveAll(others);
    }

    /** Requirement #6: director sign-off is an explicit, audited action. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public CampaignCardResponse approveCard(UUID cardId, boolean approved) {
        CampaignCard card = requireCard(cardId);
        card.setApprovalStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        card.setApprovedBy(BrandContext.getCurrentUserId());
        card.setApprovedAt(Instant.now());
        CampaignCard persisted = cardRepository.save(card);
        return withResolvedHandle(cardMapper.toResponse(persisted), persisted.getCreatorId());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER')")
    public CampaignCardResponse updatePaymentStatus(UUID cardId, PaymentStatus status) {
        CampaignCard card = requireCard(cardId);
        card.setPaymentStatus(status);
        CampaignCard persisted = cardRepository.save(card);
        return withResolvedHandle(cardMapper.toResponse(persisted), persisted.getCreatorId());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public void deleteCard(UUID cardId) {
        CampaignCard card = requireCard(cardId);
        card.setDeletedAt(Instant.now());
        cardRepository.save(card);
    }

    /** Requirement #8. Q-J14: capped, and each card validated before anything is written. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR', 'ACCOUNT_MANAGER', 'ACCOUNT_EXECUTIVE')")
    public Map<String, Object> bulkMoveCards(List<UUID> cardIds, UUID targetColumnId) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw ApiException.badRequest("Select at least one card");
        }
        if (cardIds.size() > MAX_BULK_CARDS) {
            throw ApiException.badRequest("You can move at most " + MAX_BULK_CARDS + " cards at once");
        }

        KanbanColumn targetColumn = columnRepository.findScopedById(targetColumnId)
                .orElseThrow(() -> ApiException.notFound("Target column"));

        List<CampaignCard> cards = cardRepository.findAllScopedByIds(cardIds);
        List<CampaignCard> moved = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();

        int position = cardRepository.findMaxPosition(targetColumnId) + 1;
        for (CampaignCard card : cards) {
            if (!targetColumn.getBoardId().equals(card.getBoardId())) {
                rejected.add(Map.of("cardId", card.getId(), "reason", "Card is on a different board"));
                continue;
            }
            try {
                enforceApprovalGate(card, targetColumn);
            } catch (ApiException e) {
                rejected.add(Map.of("cardId", card.getId(), "reason", e.getMessage()));
                continue;
            }
            UUID fromColumnId = card.getColumnId();
            card.setColumnId(targetColumnId);
            card.setPosition(position++);
            moved.add(card);
            eventPublisher.publishEvent(new CardMovedEvent(
                    card.getId(), fromColumnId, targetColumnId, card.getBrandId(), Instant.now()));
        }

        cardRepository.saveAll(moved);
        return Map.of("moved", moved.size(), "rejected", rejected);
    }

    // ------------------------------------------------------------- comments

    @Transactional(readOnly = true)
    public List<CardCommentResponse> getComments(UUID cardId) {
        requireCard(cardId);
        return commentRepository.findForCard(cardId).stream()
                .map(c -> new CardCommentResponse(c.getId(), c.getCardId(), c.getAuthorId(),
                        c.getAuthorName(), c.getBody(), c.getCreatedAt()))
                .toList();
    }

    public CardCommentResponse addComment(UUID cardId, String body, String authorName) {
        CampaignCard card = requireCard(cardId);
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("Comment cannot be empty");
        }

        CardComment comment = new CardComment();
        comment.setCardId(card.getId());
        comment.setAuthorId(BrandContext.getCurrentUserId());
        comment.setAuthorName(authorName);
        comment.setBody(body.trim());

        CardComment saved = commentRepository.save(comment);
        return new CardCommentResponse(saved.getId(), saved.getCardId(), saved.getAuthorId(),
                saved.getAuthorName(), saved.getBody(), saved.getCreatedAt());
    }

    public void deleteComment(UUID commentId) {
        CardComment comment = commentRepository.findScopedById(commentId)
                .orElseThrow(() -> ApiException.notFound("Comment"));
        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
    }

    // ---------------------------------------------------------- saved views

    @Transactional(readOnly = true)
    public List<SavedViewResponse> listSavedViews() {
        return savedViewRepository
                .findVisible(BrandContext.requireBrandId(), BrandContext.getCurrentUserId()).stream()
                .map(v -> new SavedViewResponse(v.getId(), v.getName(), v.getScope(), v.getFilter(), v.isShared()))
                .toList();
    }

    public SavedViewResponse createSavedView(String name, String scope,
                                             Map<String, Object> filter, boolean shared) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("View name is required");
        }
        SavedView view = new SavedView();
        view.setBrandId(BrandContext.requireBrandId());
        view.setUserId(BrandContext.getCurrentUserId());
        view.setName(name.trim());
        view.setScope(scope != null ? scope : "BOARD");
        view.setFilter(filter != null ? filter : Map.of());
        view.setShared(shared);
        SavedView saved = savedViewRepository.save(view);
        return new SavedViewResponse(saved.getId(), saved.getName(), saved.getScope(),
                saved.getFilter(), saved.isShared());
    }

    public void deleteSavedView(UUID id) {
        SavedView view = savedViewRepository
                .findOwned(id, BrandContext.requireBrandId(), BrandContext.getCurrentUserId())
                .orElseThrow(() -> ApiException.notFound("Saved view"));
        savedViewRepository.delete(view);
    }

    // -------------------------------------------------------------- helpers

    private CampaignCard requireCard(UUID cardId) {
        return cardRepository.findByIdAndBrandId(cardId)
                .orElseThrow(() -> ApiException.notFound("Card"));
    }

    private BoardWithCardsResponse.ColumnWithCardsResponse toColumnResponse(KanbanColumn column) {
        return new BoardWithCardsResponse.ColumnWithCardsResponse(
                column.getId(), column.getName(), column.getDisplayOrder(),
                column.isRequiresDirectorApproval(), column.isRequiresClientApproval(),
                column.isTriggersEmail(), column.getTriggerTemplateId(), List.of());
    }

    /** MapStruct cannot reach across the module boundary, so the handle is grafted on here. */
    private CampaignCardResponse withHandle(CampaignCardResponse response, String handle) {
        return new CampaignCardResponse(
                response.id(), response.boardId(), response.columnId(), response.brandId(),
                response.creatorId(), handle, response.campaignId(), response.position(),
                response.briefId(), response.assigneeId(), response.blocked(),
                response.deliverables(), response.feeAmount(), response.feeCurrency(),
                response.deadline(), response.paymentStatus(), response.contentDraftUrls(),
                response.approvalStatus(), response.approvedBy(), response.approvedAt(),
                response.notes());
    }

    private CampaignCardResponse withResolvedHandle(CampaignCardResponse response, UUID creatorId) {
        return withHandle(response, creatorLookup.findContact(creatorId)
                .map(CreatorLookupPort.CreatorContact::handle).orElse(null));
    }
}
