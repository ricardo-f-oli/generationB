package com.generationb.coverage;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The coverage module's published shapes (requirements #11–#15). */
public final class CoverageDtos {

    private CoverageDtos() {
    }

    public record CoverageItemResponse(
            UUID id,
            UUID campaignId,
            UUID creatorId,
            String creatorHandle,
            String platform,
            String postType,
            String contentForm,
            String url,
            String caption,
            /** Null where the platform publishes no view count — Instagram never does. Not zero. */
            Long views,
            long likes,
            long comments,
            Long shares,
            Long saves,
            Long impressions,
            BigDecimal er,
            /**
             * The brand or campaign term found in the caption, as it appeared. Null means the
             * post matched nothing, which is the honest answer to "why is this in the log?"
             */
            String matchedTerm,
            String standardizedName,
            boolean unsolicited,
            String source,
            Instant postedAt) {
    }

    public record CreateCoverageCommand(
            UUID campaignId,
            UUID creatorId,
            @NotBlank(message = "The creator handle is required") String creatorHandle,
            String platform,
            String postType,
            String url,
            String caption,
            Long views,
            Long likes,
            Long comments,
            Long shares,
            Long saves,
            Long impressions,
            Boolean unsolicited,
            Instant postedAt) {
    }

    /** Requirement #11: what an auto-clip pass found. */
    public record ClipResult(int captured, int duplicates, List<CoverageItemResponse> items) {
    }

    public record DigestSettingsResponse(
            boolean enabled,
            String sendTime,
            String recipientEmail,
            String clippingNamePattern,
            boolean includeUnsolicited,
            Instant lastSentAt) {
    }

    public record UpdateDigestSettingsCommand(
            Boolean enabled,
            String sendTime,
            String recipientEmail,
            String clippingNamePattern,
            Boolean includeUnsolicited) {
    }
}
