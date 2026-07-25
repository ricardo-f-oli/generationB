package com.generationb.outreach.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.outreach.*;
import com.generationb.outreach.internal.OutreachTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/outreach/templates")
public class OutreachTemplateController {

    private final OutreachTemplateService templateService;

    public OutreachTemplateController(OutreachTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> listTemplates(@RequestParam(required = false) UUID brandId) {
        List<TemplateResponse> templates = templateService.listTemplates(brandId);
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(@RequestBody CreateTemplateCommand command) {
        TemplateResponse template = templateService.createTemplate(command);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(@PathVariable UUID id) {
        TemplateResponse template = templateService.getTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(@PathVariable UUID id, @RequestBody UpdateTemplateCommand command) {
        TemplateResponse template = templateService.updateTemplate(id, command);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateTemplate(@PathVariable UUID id) {
        templateService.deactivateTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/ai-generate")
    public ResponseEntity<ApiResponse<TemplateResponse>> generateAiTemplate(@RequestBody GenerateAiTemplateCommand command) {
        TemplateResponse template = templateService.generateAiTemplate(command);
        return ResponseEntity.ok(ApiResponse.success(template));
    }
}
