package com.generationb.campaigns.api;

import com.generationb.campaigns.*;
import com.generationb.campaigns.internal.KanbanService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class KanbanController {

    private final KanbanService kanbanService;

    public KanbanController(KanbanService kanbanService) {
        this.kanbanService = kanbanService;
    }

    @PostMapping("/campaigns/{id}/boards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> createBoard(
            @PathVariable("id") UUID campaignId,
            @Valid @RequestBody CreateBoardCommand command) {
        return ApiResponse.of(kanbanService.createBoard(campaignId, command));
    }

    @GetMapping("/boards/{boardId}")
    public ApiResponse<BoardWithCardsResponse> getBoardWithCards(@PathVariable UUID boardId) {
        return ApiResponse.of(kanbanService.getBoardWithCards(boardId));
    }

    @PostMapping("/boards/{boardId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CampaignCardResponse> createCard(
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateCardCommand command) {
        return ApiResponse.of(kanbanService.createCard(boardId, command));
    }

    @PatchMapping("/cards/{cardId}/move")
    public ApiResponse<CampaignCardResponse> moveCard(
            @PathVariable UUID cardId,
            @RequestBody MoveCardRequest request) {
        return ApiResponse.of(kanbanService.moveCard(cardId, request.targetColumnId()));
    }

    @PatchMapping("/cards/{cardId}")
    public ApiResponse<CampaignCardResponse> updateCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdateCardCommand command) {
        return ApiResponse.of(kanbanService.updateCard(cardId, command));
    }

    @PatchMapping("/cards/{cardId}/payment-status")
    public ApiResponse<CampaignCardResponse> updatePaymentStatus(
            @PathVariable UUID cardId,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return ApiResponse.of(kanbanService.updatePaymentStatus(cardId, request.status()));
    }

    @PostMapping("/boards/{boardId}/cards/bulk-move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkMoveCards(
            @PathVariable UUID boardId,
            @Valid @RequestBody BulkMoveCardsRequest request) {
        kanbanService.bulkMoveCards(request.cardIds(), request.targetColumnId());
    }
}
