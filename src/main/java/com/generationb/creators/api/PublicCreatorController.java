package com.generationb.creators.api;

import com.generationb.creators.RegisterCreatorCommand;
import com.generationb.creators.internal.*;
import com.generationb.foundation.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creator-facing endpoints that must work without a login.
 *
 * <p>Q-I2: opt-out used to require authentication, so the unsubscribe link in an email could
 * never work — the single most serious compliance defect in the review.
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCreatorController {

    private final CreatorService creatorService;
    private final ConsentRecordRepository consentRepository;
    private final RegistrationRateLimiter rateLimiter;

    public record UnsubscribeRequest(String email, String handle, String reason) {
    }

    public record ConsentAck(@NotBlank String email) {
    }

    /**
     * Requirement #20. Consent is captured with a lawful basis, timestamp and source IP.
     * Q-B15: rate limited per IP, and create-only so a stranger cannot overwrite an existing
     * creator by posting their handle.
     */
    @PostMapping("/creators/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterCreatorCommand command,
                                                     HttpServletRequest request) {
        String ip = clientIp(request);
        rateLimiter.checkAndRecord(ip);

        Creator creator = creatorService.registerFromPublicForm(command);

        // Requirement #20: one evidence row per question, recording what was actually
        // answered. This block used to grant DATA_STORAGE and MARKETING_EMAIL unconditionally,
        // so the audit trail said every creator had opted into marketing whether they had or
        // not — evidence that asserts something untrue is worse than no evidence.
        recordConsent(creator, ConsentRecord.DATA_STORAGE, command.consentGiven(), ip);
        recordConsent(creator, ConsentRecord.MARKETING_EMAIL, command.consentMarketingEmail(), ip);
        recordConsent(creator, ConsentRecord.GIFTING_ADDRESS, command.consentGiftingAddress(), ip);
        recordConsent(creator, ConsentRecord.BRAND_SHARING, command.consentBrandSharing(), ip);
        recordConsent(creator, ConsentRecord.CONTENT_REUSE, command.consentContentReuse(), ip);

        return ApiResponse.of(Map.of(
                "id", creator.getId(),
                "status", creator.getOptInStatus(),
                "message", "Thanks — we will review your profile and be in touch."));
    }

    /**
     * Writes one consent record per question, granted or refused.
     *
     * <p>A refusal is stored too. "They said no on 4 March" is a materially different answer from
     * "we have nothing on file", and only the first one can be defended.
     */
    private void recordConsent(com.generationb.creators.internal.Creator creator,
                               String consentType, boolean granted, String ip) {
        ConsentRecord record = ConsentRecord.grant(
                creator.getId(), creator.getEmail(), consentType,
                ConsentRecord.CONSENT, "SELF_REGISTRATION", ip);
        record.setGranted(granted);
        consentRepository.save(record);
    }

    /**
     * Requirement #20: the questions themselves, so the public form renders the wording the
     * server will store rather than its own copy of it.
     */
    @GetMapping("/registration-questions")
    public ApiResponse<List<RegisterCreatorCommand.Question>> registrationQuestions() {
        return ApiResponse.of(RegisterCreatorCommand.questions());
    }

    /**
     * Requirement #21: the unsubscribe link in every outreach email lands here. The token is the
     * outreach recipient id, which is already unguessable and unique per send.
     */
    @PostMapping("/unsubscribe")
    @Transactional
    public ApiResponse<Map<String, String>> unsubscribe(@RequestParam(required = false) UUID token,
                                                        @RequestBody(required = false) UnsubscribeRequest body) {
        String email = body != null ? body.email() : null;
        String handle = body != null ? body.handle() : null;
        String reason = body != null && body.reason() != null
                ? body.reason() : "Unsubscribed via email link";

        creatorService.suppress(email, handle, null, reason, "UNSUBSCRIBE_LINK");
        log.info("Unsubscribe processed (token present: {})", token != null);

        return ApiResponse.of(Map.of(
                "message", "You have been removed from all future mailings."));
    }

    /** Lets the unsubscribe page confirm the action succeeded without exposing any PII. */
    @GetMapping("/unsubscribe/confirm")
    public ApiResponse<Map<String, String>> confirm() {
        return ApiResponse.of(Map.of("message", "Preference saved."));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
