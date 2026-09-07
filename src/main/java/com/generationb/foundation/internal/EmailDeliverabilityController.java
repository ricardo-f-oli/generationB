package com.generationb.foundation.internal;

import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requirement #28: is the sending domain actually authenticated?
 *
 * <p>The DNS records cannot be created from here — they belong to whoever runs the domain — so
 * this answers the question instead of asking it. Without it, "is DKIM done yet?" gets passed
 * between two people who each assume the other did it, while mail quietly lands in spam.
 *
 * <p>Read-only, and admin-only because the report names the exact hosts an attacker would want
 * to know are unprotected.
 */
@RestController
@RequestMapping("/api/settings/email")
@RequiredArgsConstructor
public class EmailDeliverabilityController {

    private final EmailDnsChecker emailDnsChecker;

    /**
     * Resolves SPF, DKIM, DMARC and the inbound MX record and grades each one.
     *
     * <p>A live lookup, not an echo of configuration. The failure this exists to catch is silent:
     * the application is configured perfectly, sends happily, and every message fails
     * authentication at the receiving end.
     */
    @GetMapping("/dns")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ApiResponse<EmailDnsChecker.Report> dns() {
        return ApiResponse.of(emailDnsChecker.check());
    }
}
