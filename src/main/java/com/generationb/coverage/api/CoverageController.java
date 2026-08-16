package com.generationb.coverage.api;

import com.generationb.coverage.internal.CoverageDigestSettings;
import com.generationb.coverage.internal.CoverageItem;
import com.generationb.coverage.internal.CoverageService;
import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coverage")
@RequiredArgsConstructor
public class CoverageController {

    private final CoverageService coverageService;

    @GetMapping("/log")
    public ApiResponse<List<CoverageItem>> getCoverageLog(@RequestParam(required = false) String brand) {
        List<CoverageItem> logItems = coverageService.getCoverageLog(brand);
        return ApiResponse.of(logItems);
    }

    @PostMapping("/export/{format}")
    public ApiResponse<Map<String, String>> exportCoverage(@PathVariable String format) {
        Map<String, String> exportData = coverageService.exportCoverage(format);
        return ApiResponse.of(exportData);
    }

    @PutMapping("/digest-settings")
    public ApiResponse<CoverageDigestSettings> updateDigestSettings(@RequestBody Map<String, Object> payload) {
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
        String sendTime = (String) payload.getOrDefault("sendTime", "08:00");
        String recipientEmail = (String) payload.get("recipientEmail");

        CoverageDigestSettings settings = coverageService.updateDigestSettings(enabled, sendTime, recipientEmail);
        return ApiResponse.of(settings);
    }
}
