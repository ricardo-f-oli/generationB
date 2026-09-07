package com.generationb.attachments;

import java.time.Instant;
import java.util.UUID;

/**
 * Requirement #5.
 *
 * <p>Note what is absent: the storage key. It is deliberately not exposed — a client that never
 * sees a key cannot replay one, and downloads go through the attachment id instead.
 */
public record AttachmentResponse(
    UUID id,
    String ownerType,
    UUID ownerId,
    String filename,
    String contentType,
    long sizeBytes,
    String humanSize,
    UUID uploadedBy,
    String uploadedByName,
    Instant createdAt
) {}
