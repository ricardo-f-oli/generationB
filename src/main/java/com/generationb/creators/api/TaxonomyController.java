package com.generationb.creators.api;

import com.generationb.creators.StyleTagResponse;
import com.generationb.creators.internal.TaxonomyService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin surfaces for the tag library (#17) and custom attribute schema (#16). */
@RestController
@RequestMapping("/api/taxonomy")
@RequiredArgsConstructor
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    public record TagRequest(@NotBlank(message = "Name is required") String name, String category) {
    }

    public record DefinitionRequest(
            @NotBlank(message = "Key is required") String key,
            @NotBlank(message = "Label is required") String label,
            String type,
            List<String> options,
            boolean required) {
    }

    public record AttributeValueRequest(UUID definitionId, String value) {
    }

    // tags

    @GetMapping("/tags")
    public ApiResponse<List<StyleTagResponse>> listTags() {
        return ApiResponse.of(taxonomyService.listTags());
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StyleTagResponse> createTag(@Valid @RequestBody TagRequest request) {
        return ApiResponse.of(taxonomyService.createTag(request.name(), request.category()));
    }

    @PatchMapping("/tags/{tagId}")
    public ApiResponse<StyleTagResponse> renameTag(@PathVariable UUID tagId,
                                                   @Valid @RequestBody TagRequest request) {
        return ApiResponse.of(taxonomyService.renameTag(tagId, request.name()));
    }

    @DeleteMapping("/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable UUID tagId) {
        taxonomyService.deleteTag(tagId);
    }

    @PutMapping("/creators/{creatorId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignTag(@PathVariable UUID creatorId, @PathVariable UUID tagId) {
        taxonomyService.assignTag(creatorId, tagId);
    }

    @DeleteMapping("/creators/{creatorId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignTag(@PathVariable UUID creatorId, @PathVariable UUID tagId) {
        taxonomyService.unassignTag(creatorId, tagId);
    }

    // custom attributes

    @GetMapping("/attributes")
    public ApiResponse<List<TaxonomyService.AttributeDefinitionResponse>> listDefinitions() {
        return ApiResponse.of(taxonomyService.listDefinitions());
    }

    @PostMapping("/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaxonomyService.AttributeDefinitionResponse> createDefinition(
            @Valid @RequestBody DefinitionRequest request) {
        return ApiResponse.of(taxonomyService.createDefinition(
                request.key(), request.label(), request.type(), request.options(), request.required()));
    }

    @DeleteMapping("/attributes/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDefinition(@PathVariable UUID definitionId) {
        taxonomyService.deleteDefinition(definitionId);
    }

    @GetMapping("/creators/{creatorId}/attributes")
    public ApiResponse<Map<String, String>> getValues(@PathVariable UUID creatorId) {
        return ApiResponse.of(taxonomyService.getAttributeValues(creatorId));
    }

    @PutMapping("/creators/{creatorId}/attributes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setValue(@PathVariable UUID creatorId,
                         @RequestBody AttributeValueRequest request) {
        taxonomyService.setAttributeValue(creatorId, request.definitionId(), request.value());
    }
}
