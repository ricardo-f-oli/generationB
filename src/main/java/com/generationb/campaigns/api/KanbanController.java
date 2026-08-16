package com.generationb.campaigns.api;

import com.generationb.campaigns.*;
import com.generationb.campaigns.internal.KanbanService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KanbanController {

    private final KanbanService kanbanService;

    public record CommentRequest(
            @NotBlank(message = "Comment cannot be empty") String body,
            String authorName) {
    }

    public record ApprovalRequest(boolean approved) {
    }

    public record ReorderColumnsRequest(List<UUID> columnIds) {
    }

    public record SavedViewRequest(
            @NotBlank(message = "Name is required") String name,
            String scope,
            Map<String, Object> filter,
            boolean shared) {
    }

    // --------------------------------------------------------------- boards

    @PostMapping("/campaigns/{id}/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> createBoard(@PathVariable("id") UUID campaignId,
                                                  @Valid @RequestBody CreateBoardCommand command) {
        return ApiResponse.of(kanbanService.createBoard(campaignId, command));
    }

    /**
     * Resolves the board for a campaign, creating it from the brand's template on first access.
     * Without this the frontend had to guess whether a board existed, which risked creating a
     * second one for the same campaign.
     */
    @GetMapping("/campaigns/{campaignId}/board")
    public ApiResponse<BoardWithCardsResponse> getBoardForCampaign(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String filter) {
        return ApiResponse.of(kanbanService.getOrCreateBoardForCampaign(campaignId, filter));
    }

    @GetMapping("/boards/{boardId}")
    public ApiResponse<BoardWithCardsResponse> getBoardWithCards(
            @PathVariable UUID boardId,
            @RequestParam(required = false) String filter) {
        return ApiResponse.of(filter == null || filter.isBlank()
                ? kanbanService.getBoardWithCards(boardId)
                : kanbanService.getFilteredBoard(boardId, filter));
    }

    // -------------------------------------------------------------- columns

    @PostMapping("/boards/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardWithCardsResponse.ColumnWithCardsResponse> addColumn(
            @PathVariable UUID boardId, @Valid @RequestBody CreateColumnCommand command) {
        return ApiResponse.of(kanbanService.addColumn(boardId, command));
    }

    @PatchMapping("/columns/{columnId}")
    public ApiResponse<BoardWithCardsResponse.ColumnWithCardsResponse> updateColumn(
            @PathVariable UUID columnId, @Valid @RequestBody CreateColumnCommand command) {
        return ApiResponse.of(kanbanService.updateColumn(columnId, command));
    }

    @PutMapping("/boards/{boardId}/columns/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderColumns(@PathVariable UUID boardId, @RequestBody ReorderColumnsRequest request) {
        kanbanService.reorderColumns(boardId, request.columnIds());
    }

    @DeleteMapping("/columns/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable UUID columnId) {
        kanbanService.deleteColumn(columnId);
    }

    // ---------------------------------------------------------------- cards

    @PostMapping("/boards/{boardId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignCardResponse> createCard(@PathVariable UUID boardId,
                                                        @Valid @RequestBody CreateCardCommand command) {
        return ApiResponse.of(kanbanService.createCard(boardId, command));
    }

    @PatchMapping("/cards/{cardId}/move")
    public ApiResponse<CampaignCardResponse> moveCard(@PathVariable UUID cardId,
                                                      @Valid @RequestBody MoveCardRequest request) {
        return ApiResponse.of(
                kanbanService.moveCard(cardId, request.targetColumnId(), request.position()));
    }

    @PatchMapping("/cards/{cardId}")
    public ApiResponse<CampaignCardResponse> updateCard(@PathVariable UUID cardId,
                                                        @Valid @RequestBody UpdateCardCommand command) {
        return ApiResponse.of(kanbanService.updateCard(cardId, command));
    }

    @PostMapping("/cards/{cardId}/approval")
    public ApiResponse<CampaignCardResponse> approveCard(@PathVariable UUID cardId,
                                                         @RequestBody ApprovalRequest request) {
        return ApiResponse.of(kanbanService.approveCard(cardId, request.approved()));
    }

    @PatchMapping("/cards/{cardId}/payment-status")
    public ApiResponse<CampaignCardResponse> updatePaymentStatus(
            @PathVariable UUID cardId, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return ApiResponse.of(kanbanService.updatePaymentStatus(cardId, request.status()));
    }

    @DeleteMapping("/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@PathVariable UUID cardId) {
        kanbanService.deleteCard(cardId);
    }

    @PostMapping("/boards/{boardId}/cards/bulk-move")
    public ApiResponse<Map<String, Object>> bulkMoveCards(@PathVariable UUID boardId,
                                                          @Valid @RequestBody BulkMoveCardsRequest request) {
        return ApiResponse.of(kanbanService.bulkMoveCards(request.cardIds(), request.targetColumnId()));
    }

    // ------------------------------------------------------------- comments

    @GetMapping("/cards/{cardId}/comments")
    public ApiResponse<List<CardCommentResponse>> getComments(@PathVariable UUID cardId) {
        return ApiResponse.of(kanbanService.getComments(cardId));
    }

    @PostMapping("/cards/{cardId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CardCommentResponse> addComment(@PathVariable UUID cardId,
                                                       @Valid @RequestBody CommentRequest request) {
        return ApiResponse.of(kanbanService.addComment(cardId, request.body(), request.authorName()));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID commentId) {
        kanbanService.deleteComment(commentId);
    }

    // ---------------------------------------------------------- saved views

    @GetMapping("/saved-views")
    public ApiResponse<List<SavedViewResponse>> listSavedViews() {
        return ApiResponse.of(kanbanService.listSavedViews());
    }

    @PostMapping("/saved-views")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavedViewResponse> createSavedView(@Valid @RequestBody SavedViewRequest request) {
        return ApiResponse.of(kanbanService.createSavedView(
                request.name(), request.scope(), request.filter(), request.shared()));
    }

    @DeleteMapping("/saved-views/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSavedView(@PathVariable UUID id) {
        kanbanService.deleteSavedView(id);
    }
}
