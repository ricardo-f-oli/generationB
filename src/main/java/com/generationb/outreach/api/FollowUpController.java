package com.generationb.outreach.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.outreach.internal.FollowUpSuggestionService;
import com.generationb.outreach.internal.FollowUpSuggestionService.SuggestionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Requirement #33: the AI-suggested follow-ups queue.
 */
@RestController
@RequestMapping("/api/outreach/follow-ups")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpSuggestionService followUpService;

    public record EditRequest(String subject, String body) {
    }

    @GetMapping
    public ApiResponse<List<SuggestionRow>> list(
            @RequestParam(required = false, defaultValue = "SUGGESTED") String status) {
        return ApiResponse.of(followUpService.list(status));
    }

    /** Runs the same scan the 08:00 schedule runs. */
    @PostMapping("/generate")
    public ApiResponse<List<SuggestionRow>> generate() {
        followUpService.generateFollowUpSuggestions();
        return ApiResponse.of(followUpService.list("SUGGESTED"));
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<SuggestionRow> regenerate(@PathVariable UUID id) {
        return ApiResponse.of(followUpService.regenerate(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SuggestionRow> edit(@PathVariable UUID id, @RequestBody EditRequest request) {
        return ApiResponse.of(followUpService.update(id, request.subject(), request.body()));
    }

    @PostMapping("/{id}/sent")
    public ApiResponse<Void> markSent(@PathVariable UUID id) {
        followUpService.markSent(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> dismiss(@PathVariable UUID id) {
        followUpService.dismiss(id);
        return ApiResponse.success();
    }
}
