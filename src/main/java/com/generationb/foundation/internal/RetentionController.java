package com.generationb.foundation.internal;

import com.generationb.foundation.ApiResponse;
import com.generationb.foundation.BrandContext;
import com.generationb.shared.RetentionSweeper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requirement #37: the GDPR screen's back end.
 *
 * <p>Under {@code /api/settings/**}, which the security config already restricts to admins, and
 * the service methods carry their own {@code @PreAuthorize} so the rule survives a route move.
 *
 * <p>The only mutating endpoint is the manual run, and it defaults to a preview. Someone opening
 * this screen to see what the policy is should not be one mis-click from deleting a year of
 * addresses.
 */
@RestController
@RequestMapping("/api/settings/retention")
@RequiredArgsConstructor
public class RetentionController {

    private final DataRetentionService retentionService;

    public record PolicyRow(String dataset, String policy, Integer lastAffected,
                            Instant lastRunAt, Instant oldestRemaining, String error) {
    }

    /**
     * The declared policies joined to the last real pass of each.
     *
     * <p>Policies come from the sweepers rather than a written list, so what the screen shows is
     * necessarily what the code does. A policy that has never run shows null dates instead of
     * being hidden — "we have a rule and it has never fired" is exactly what someone needs to see.
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> status() {
        Map<String, RetentionRun> latest = new LinkedHashMap<>();
        for (RetentionRun run : retentionService.latestPerDataset()) {
            latest.putIfAbsent(run.getDataset(), run);
        }

        List<PolicyRow> rows = retentionService.declaredPolicies().stream()
                .map(policy -> {
                    RetentionRun run = latest.get(policy.dataset());
                    return new PolicyRow(
                            policy.dataset(),
                            policy.policy(),
                            run == null ? null : run.getAffected(),
                            run == null ? null : run.getRanAt(),
                            run == null ? null : run.getOldestRemaining(),
                            run == null ? null : run.getError());
                })
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", retentionService.isEnabled());
        body.put("schedule", "03:00 Europe/London, nightly");
        body.put("policies", rows);
        return ApiResponse.of(body);
    }

    /** The evidence log itself, newest first. */
    @GetMapping("/runs")
    public ApiResponse<List<RetentionRun>> runs(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(retentionService.recentRuns(limit));
    }

    /**
     * Runs the policies now.
     *
     * <p>{@code dryRun} defaults to true. Deleting personal data on an unqualified POST would be
     * the wrong default for a screen whose main job is showing people what the rules are.
     */
    @PostMapping("/run")
    public ApiResponse<List<RetentionSweeper.Outcome>> run(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        UUID triggeredBy = BrandContext.getCurrentUserId();
        return ApiResponse.of(retentionService.runAll(dryRun, triggeredBy));
    }
}
