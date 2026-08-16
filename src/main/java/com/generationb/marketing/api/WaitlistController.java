package com.generationb.marketing.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.marketing.internal.WaitlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requirement #48: the marketing waitlist. */
@RestController
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    public record JoinRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,
            String name,
            String handle,
            String platform,
            String niche,
            boolean consentGiven,
            String source) {
    }

    public record ConvertRequest(UUID creatorId) {
    }

    // ------------------------------------------------------- public surface

    @PostMapping("/api/public/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> join(@Valid @RequestBody JoinRequest request) {
        WaitlistService.WaitlistView view = waitlistService.join(
                request.email(), request.name(), request.handle(),
                request.platform(), request.niche(), request.consentGiven(), request.source());
        return ApiResponse.of(Map.of(
                "id", view.id(),
                "status", view.status(),
                "message", "You are on the list. We will be in touch when Generation B opens up."));
    }

    @GetMapping("/api/public/waitlist/confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestParam String token) {
        WaitlistService.WaitlistView view = waitlistService.confirm(token);
        return ApiResponse.of(Map.of("status", view.status(), "message", "Email confirmed. Thank you."));
    }

    // -------------------------------------------------------- staff surface

    @GetMapping("/api/marketing/waitlist")
    public ApiResponse<List<WaitlistService.WaitlistView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<WaitlistService.WaitlistView> result = waitlistService.list(
                status, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/api/marketing/waitlist/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.of(waitlistService.stats());
    }

    @PostMapping("/api/marketing/waitlist/{id}/convert")
    public ApiResponse<WaitlistService.WaitlistView> convert(@PathVariable UUID id,
                                                             @RequestBody ConvertRequest request) {
        return ApiResponse.of(waitlistService.markConverted(id, request.creatorId()));
    }

    @PostMapping("/api/marketing/waitlist/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID id) {
        waitlistService.reject(id);
    }
}
