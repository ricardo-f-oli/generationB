package com.generationb.marketing.internal;

import com.generationb.foundation.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Requirement #48. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    public record WaitlistView(
            UUID id, String email, String name, String handle, String primaryPlatform,
            String niche, String status, Instant createdAt, Instant convertedAt) {
    }

    /** Public: someone signs up on the landing page. Idempotent on email. */
    @Transactional
    public WaitlistView join(String email, String name, String handle, String platform,
                             String niche, boolean consentGiven, String source) {
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("Email is required");
        }
        if (!consentGiven) {
            throw ApiException.badRequest("Please accept the privacy policy to join the waitlist");
        }

        String normalised = email.trim().toLowerCase();
        WaitlistEntry entry = waitlistRepository.findByEmailIgnoreCase(normalised)
                .orElseGet(WaitlistEntry::new);

        entry.setEmail(normalised);
        if (name != null && !name.isBlank()) entry.setName(name.trim());
        if (handle != null && !handle.isBlank()) entry.setHandle(handle.trim().replaceFirst("^@", ""));
        if (platform != null && !platform.isBlank()) entry.setPrimaryPlatform(platform.toUpperCase());
        if (niche != null && !niche.isBlank()) entry.setNiche(niche.trim());
        entry.setConsentGiven(true);
        if (source != null && !source.isBlank()) entry.setSource(source);
        if (entry.getConfirmToken() == null) {
            entry.setConfirmToken(newConfirmToken());
        }

        return toView(waitlistRepository.save(entry));
    }

    /** Public: double opt-in confirmation from the emailed link. */
    @Transactional
    public WaitlistView confirm(String token) {
        WaitlistEntry entry = waitlistRepository.findByConfirmToken(token)
                .orElseThrow(() -> ApiException.badRequest("That confirmation link is not valid"));
        if (WaitlistEntry.PENDING.equals(entry.getStatus())) {
            entry.setStatus(WaitlistEntry.CONFIRMED);
            entry.setConfirmedAt(Instant.now());
            waitlistRepository.save(entry);
        }
        return toView(entry);
    }

    @Transactional(readOnly = true)
    public Page<WaitlistView> list(String status, Pageable pageable) {
        return waitlistRepository.findByStatus(status, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending", waitlistRepository.countByStatus(WaitlistEntry.PENDING));
        stats.put("confirmed", waitlistRepository.countByStatus(WaitlistEntry.CONFIRMED));
        stats.put("converted", waitlistRepository.countByStatus(WaitlistEntry.CONVERTED));
        stats.put("total", waitlistRepository.count());
        return stats;
    }

    /**
     * Marks a waitlist entry as converted once it has been turned into a creator.
     * The actual creator is created by the creators module through its own API, keeping the
     * module boundary intact.
     */
    @Transactional
    public WaitlistView markConverted(UUID id, UUID creatorId) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Waitlist entry"));
        entry.setStatus(WaitlistEntry.CONVERTED);
        entry.setConvertedCreatorId(creatorId);
        entry.setConvertedAt(Instant.now());
        return toView(waitlistRepository.save(entry));
    }

    @Transactional
    public void reject(UUID id) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Waitlist entry"));
        entry.setStatus(WaitlistEntry.REJECTED);
        waitlistRepository.save(entry);
    }

    private WaitlistView toView(WaitlistEntry entry) {
        return new WaitlistView(entry.getId(), entry.getEmail(), entry.getName(), entry.getHandle(),
                entry.getPrimaryPlatform(), entry.getNiche(), entry.getStatus(),
                entry.getCreatedAt(), entry.getConvertedAt());
    }

    /**
     * The confirmation token only has to be unguessable; keeping it local avoids depending on
     * another module's internals (which the Modulith boundary test correctly rejected).
     */
    private static String newConfirmToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
