package com.generationb.creators.api;

import com.generationb.creators.internal.ShortlistService;
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
@RequestMapping("/api/shortlists")
@RequiredArgsConstructor
public class ShortlistController {

    private final ShortlistService shortlistService;

    public record CreateShortlistRequest(
            @NotBlank(message = "Name is required") String name,
            String visibility,
            UUID campaignId,
            List<UUID> creatorIds) {
    }

    public record UpdateShortlistRequest(String name, String visibility) {
    }

    public record AddCreatorsRequest(List<UUID> creatorIds) {
    }

    public record PromoteRequest(UUID campaignId) {
    }

    @GetMapping
    public ApiResponse<List<ShortlistService.ShortlistSummary>> list() {
        return ApiResponse.of(shortlistService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ShortlistService.ShortlistDetail> get(@PathVariable UUID id) {
        return ApiResponse.of(shortlistService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShortlistService.ShortlistDetail> create(
            @Valid @RequestBody CreateShortlistRequest request) {
        return ApiResponse.of(shortlistService.createShortlist(
                request.name(), request.visibility(), request.campaignId(), request.creatorIds()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ShortlistService.ShortlistDetail> update(
            @PathVariable UUID id, @RequestBody UpdateShortlistRequest request) {
        return ApiResponse.of(shortlistService.rename(id, request.name(), request.visibility()));
    }

    @PostMapping("/{id}/creators")
    public ApiResponse<ShortlistService.ShortlistDetail> addCreators(
            @PathVariable UUID id, @RequestBody AddCreatorsRequest request) {
        return ApiResponse.of(shortlistService.addCreators(id, request.creatorIds()));
    }

    @DeleteMapping("/{id}/creators/{creatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCreator(@PathVariable UUID id, @PathVariable UUID creatorId) {
        shortlistService.removeCreator(id, creatorId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        shortlistService.delete(id);
    }

    @PostMapping("/{id}/promote-to-campaign")
    public ApiResponse<Map<String, Object>> promote(@PathVariable UUID id,
                                                    @RequestBody PromoteRequest request) {
        return ApiResponse.of(shortlistService.promoteToCampaign(id, request.campaignId()));
    }
}
