package com.generationb.attachments.internal;

import com.generationb.attachments.AttachmentResponse;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.User;
import com.generationb.foundation.UserRepository;
import com.generationb.foundation.storage.FileStoragePort;
import com.generationb.foundation.storage.StorageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Requirement #5: files attached to a card, brief, report or creator.
 *
 * <p>The metadata row is the authority. A caller names an attachment by its id and the storage
 * key is looked up here, so a key can never arrive from outside — which is what stops one
 * tenant's key being replayed against another's bucket prefix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** Long enough to start a large download, short enough that a leaked link is stale fast. */
    private static final Duration LINK_TTL = Duration.ofMinutes(10);

    private final AttachmentRepository attachmentRepository;
    private final FileStoragePort storage;
    private final UserRepository userRepository;

    @Transactional
    public AttachmentResponse upload(String ownerType, UUID ownerId, MultipartFile file) {
        UUID brandId = BrandContext.requireBrandId();

        if (!Attachment.isValidOwnerType(ownerType)) {
            throw ApiException.badRequest("Unknown attachment target: " + ownerType);
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file was uploaded.");
        }
        // Validated before a byte is written, so an oversized or disallowed file never reaches
        // storage and never needs cleaning up.
        StorageKeys.validate(file.getContentType(), file.getSize());

        FileStoragePort.StoredFile stored;
        try (var stream = file.getInputStream()) {
            stored = storage.upload(brandId, ownerType.toLowerCase(Locale.ROOT),
                    file.getOriginalFilename(), file.getContentType(), file.getSize(), stream);
        } catch (IOException e) {
            log.error("Could not read the uploaded file", e);
            throw ApiException.badRequest("That upload was interrupted. Please try again.");
        }

        Attachment attachment = new Attachment();
        attachment.setBrandId(brandId);
        attachment.setOwnerType(ownerType);
        attachment.setOwnerId(ownerId);
        attachment.setFilename(stored.filename());
        attachment.setContentType(stored.contentType());
        attachment.setSizeBytes(stored.sizeBytes());
        attachment.setStorageKey(stored.key());
        attachment.setUploadedBy(BrandContext.getCurrentUserId());

        try {
            return toResponse(attachmentRepository.save(attachment), names(List.of(attachment)));
        } catch (RuntimeException e) {
            // The row failed but the bytes are already in the bucket. Remove them rather than
            // leaving an object nothing references and nobody can reach.
            storage.delete(brandId, stored.key());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String ownerType, UUID ownerId) {
        BrandContext.requireBrandId();
        List<Attachment> rows = attachmentRepository.findForOwner(ownerType, ownerId);
        Map<UUID, String> names = names(rows);
        return rows.stream().map(a -> toResponse(a, names)).toList();
    }

    /** Counts per owner, for the badge on a board card. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countsFor(String ownerType, List<UUID> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.countByOwner(ownerType, ownerIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    /**
     * Where the bytes come from. Prefers a signed URL so a 200MB video does not occupy an
     * application thread on a 512MB instance; falls back to streaming when the provider cannot
     * sign (local development).
     */
    @Transactional(readOnly = true)
    public Download download(UUID attachmentId) {
        UUID brandId = BrandContext.requireBrandId();
        Attachment attachment = require(attachmentId);

        Optional<String> url = storage.signedUrl(brandId, attachment.getStorageKey(), LINK_TTL);
        if (url.isPresent()) {
            return new Download(url.get(), null, attachment.getFilename(),
                    attachment.getContentType(), attachment.getSizeBytes());
        }
        FileStoragePort.FileContent content = storage.download(brandId, attachment.getStorageKey());
        return new Download(null, content, attachment.getFilename(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    @Transactional
    public void delete(UUID attachmentId) {
        UUID brandId = BrandContext.requireBrandId();
        Attachment attachment = require(attachmentId);

        attachment.setDeletedAt(Instant.now());
        attachmentRepository.save(attachment);
        // Soft-deleted in the database, actually removed from the bucket — storage costs money
        // and a "deleted" file that is still downloadable is not deleted.
        storage.delete(brandId, attachment.getStorageKey());
    }

    /** Either a redirect target or a stream, never both. */
    public record Download(String signedUrl, FileStoragePort.FileContent content,
                           String filename, String contentType, long sizeBytes) {
        public boolean isRedirect() {
            return signedUrl != null;
        }
    }

    private Attachment require(UUID id) {
        return attachmentRepository.findScopedById(id)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
    }

    private Map<UUID, String> names(List<Attachment> rows) {
        List<UUID> ids = rows.stream()
                .map(Attachment::getUploadedBy).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getName() != null ? u.getName() : u.getEmail(), (a, b) -> a));
    }

    private AttachmentResponse toResponse(Attachment a, Map<UUID, String> names) {
        return new AttachmentResponse(a.getId(), a.getOwnerType(), a.getOwnerId(),
                a.getFilename(), a.getContentType(), a.getSizeBytes(), humanSize(a.getSizeBytes()),
                a.getUploadedBy(),
                a.getUploadedBy() == null ? null : names.getOrDefault(a.getUploadedBy(), "Unknown"),
                a.getCreatedAt());
    }

    /** "2.4 MB" rather than 2516582 — the list is read by people, not scripts. */
    static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.UK, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.UK, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
