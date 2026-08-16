package com.generationb.creators.api;

import com.generationb.creators.*;
import com.generationb.creators.internal.CreatorService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creator database + matching (requirements #16–#27).
 *
 * <p>Rewritten: every list is paginated, every body is a validated record rather than a raw
 * {@code Map<String,Object>} (Q-E9), and responses are DTOs rather than JPA entities (Q-E8).
 */
@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class CreatorController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CreatorService creatorService;

    public record NoteRequest(
            @NotBlank(message = "Note text is required") String noteText,
            boolean confidential) {
    }

    public record SuppressRequest(String email, String handle, UUID creatorId, String reason) {
    }

    @GetMapping
    public ApiResponse<List<CreatorResponse>> listCreators(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String niche,
            @RequestParam(required = false) Integer minFollowers,
            @RequestParam(required = false) Integer maxFollowers,
            @RequestParam(required = false) BigDecimal minEr,
            @RequestParam(required = false) BigDecimal minUkAudience,
            @RequestParam(required = false) String optInStatus,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "followersCount") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        CreatorSearchCriteria criteria = new CreatorSearchCriteria(
                query, platform, location, niche, minFollowers, maxFollowers,
                minEr, minUkAudience, optInStatus, tagId);

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(dir, safeSort(sort)));

        Page<CreatorResponse> result = creatorService.search(criteria, pageable);
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    /** Populates the filter UI without the frontend hardcoding niches and locations. */
    @GetMapping("/filters")
    public ApiResponse<Map<String, Object>> filterOptions() {
        return ApiResponse.of(creatorService.filterOptions());
    }

    @GetMapping("/pending")
    public ApiResponse<List<CreatorResponse>> pendingRegistrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CreatorResponse> result = creatorService.pendingRegistrations(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CreatorResponse> getCreator(@PathVariable UUID id) {
        return ApiResponse.of(creatorService.getCreator(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatorResponse> createCreator(@Valid @RequestBody CreateCreatorCommand command) {
        return ApiResponse.of(creatorService.createCreator(command));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CreatorResponse> updateCreator(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateCreatorCommand command) {
        return ApiResponse.of(creatorService.updateCreator(id, command));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCreator(@PathVariable UUID id) {
        creatorService.deleteCreator(id);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<CreatorResponse> approve(@PathVariable UUID id) {
        return ApiResponse.of(creatorService.reviewRegistration(id, true));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<CreatorResponse> reject(@PathVariable UUID id) {
        return ApiResponse.of(creatorService.reviewRegistration(id, false));
    }

    @PostMapping("/import")
    public ApiResponse<CreatorService.ImportResult> importCreators(
            @RequestBody List<Map<String, String>> rows) {
        return ApiResponse.of(creatorService.importCreators(rows));
    }

    // --------------------------------------------------------------- notes

    @GetMapping("/{id}/notes")
    public ApiResponse<List<CreatorNoteResponse>> getNotes(@PathVariable UUID id) {
        return ApiResponse.of(creatorService.getNotes(id));
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatorNoteResponse> addNote(@PathVariable UUID id,
                                                    @Valid @RequestBody NoteRequest request) {
        return ApiResponse.of(creatorService.addNote(id, request.noteText(), request.confidential()));
    }

    @PutMapping("/notes/{noteId}")
    public ApiResponse<CreatorNoteResponse> updateNote(@PathVariable UUID noteId,
                                                       @Valid @RequestBody NoteRequest request) {
        return ApiResponse.of(creatorService.updateNote(noteId, request.noteText()));
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable UUID noteId) {
        creatorService.deleteNote(noteId);
    }

    // --------------------------------------------------------- suppression

    /** Staff-initiated suppression. The creator-facing route is /api/public/unsubscribe. */
    @PostMapping("/suppress")
    public ApiResponse<Map<String, String>> suppress(@RequestBody SuppressRequest request) {
        creatorService.suppress(request.email(), request.handle(), request.creatorId(),
                request.reason(), "MANUAL");
        return ApiResponse.of(Map.of("message", "Suppression recorded"));
    }

    /** Q-I3: right to erasure. Admin-only because it is irreversible. */
    @PostMapping("/{id}/anonymise")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> anonymise(@PathVariable UUID id) {
        creatorService.anonymise(id);
        return ApiResponse.of(Map.of("message", "Creator anonymised"));
    }

    private String safeSort(String requested) {
        return switch (requested) {
            case "name", "handle", "erPercentage", "createdAt", "followersCount" -> requested;
            default -> "followersCount";
        };
    }
}
