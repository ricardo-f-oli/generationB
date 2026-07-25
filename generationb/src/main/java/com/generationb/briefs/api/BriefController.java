package com.generationb.briefs.api;

import com.generationb.briefs.*;
import com.generationb.briefs.internal.BriefService;
import com.generationb.foundation.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/briefs")
public class BriefController {

    private final BriefService briefService;

    public BriefController(BriefService briefService) {
        this.briefService = briefService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BriefResponse> createBrief(@Valid @RequestBody CreateBriefCommand command) {
        return ApiResponse.of(briefService.createBrief(command));
    }

    @GetMapping
    public ApiResponse<List<BriefResponse>> listBriefs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BriefResponse> pageResult = briefService.listBriefs(PageRequest.of(page, size));
        return ApiResponse.of(
                pageResult.getContent(),
                ApiResponse.Meta.of(pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements(), pageResult.getTotalPages())
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<BriefResponse> getBrief(@PathVariable UUID id) {
        return ApiResponse.of(briefService.getBrief(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BriefResponse> updateBrief(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBriefCommand command) {
        return ApiResponse.of(briefService.updateBrief(id, command));
    }

    @PostMapping("/{id}/generate")
    public ApiResponse<BriefResponse> generateAiBrief(@PathVariable UUID id) {
        return ApiResponse.of(briefService.generateAiBrief(id));
    }

    @GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportBriefAsPdf(@PathVariable UUID id) {
        byte[] pdfBytes = briefService.exportBriefAsPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "brief-" + id + ".pdf");
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/share-link")
    public ApiResponse<Map<String, String>> getSharedBriefLink(@PathVariable UUID id) {
        String link = briefService.getSharedBriefLink(id);
        return ApiResponse.of(Map.of("shareLink", link));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrief(@PathVariable UUID id) {
        briefService.deleteBrief(id);
    }

    @GetMapping("/share/{token}")
    public ApiResponse<BriefResponse> getSharedBrief(@PathVariable String token) {
        return ApiResponse.of(briefService.getSharedBrief(token));
    }
}
