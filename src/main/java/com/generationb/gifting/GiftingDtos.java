package com.generationb.gifting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The gifting module's published shapes (requirements #41–#48).
 *
 * <p>Q-E8/Q-E9: the controller used to take {@code Map<String,Object>} in and hand JPA entities
 * back out, so nothing was validated and the API surface changed whenever the schema did.
 */
public final class GiftingDtos {

    private GiftingDtos() {
    }

    // ------------------------------------------------------------------ runs

    public record CreateRunCommand(
            @NotBlank(message = "Give the run a name") String name,
            UUID campaignId,
            String productName,
            String mailerText) {
    }

    public record RunResponse(
            UUID id,
            String name,
            UUID campaignId,
            String productName,
            String mailerText,
            String compSlipStatus,
            UUID approvedBy,
            Instant approvedAt,
            int dispatchCount,
            Instant createdAt) {
    }

    public record ApproveCompSlipCommand(String mailerText) {
    }

    // ------------------------------------------------------------ dispatches

    /** Requirement #45: creating dispatches from a set of creators. */
    public record CreateDispatchesCommand(
            UUID giftingRunId,
            @NotEmpty(message = "Select at least one creator") List<UUID> creatorIds,
            String productName,
            String sku,
            String packagingNotes,
            String courier,
            LocalDate plannedDispatchDate,
            LocalDate contentDeadline) {
    }

    /** What actually happened, rather than {@code {"success": true}}. */
    public record DispatchCreationResult(
            int created,
            int skippedNoAddress,
            int skippedExcluded,
            int skippedDuplicate,
            List<String> warnings,
            List<DispatchResponse> dispatches) {
    }

    public record DispatchResponse(
            UUID id,
            UUID giftingRunId,
            UUID creatorId,
            String handle,
            String creatorName,
            String productName,
            String sku,
            String courier,
            String trackingNumber,
            String status,
            String addressStatus,
            boolean gdprConsent,
            LocalDate plannedDispatchDate,
            LocalDate contentDeadline,
            Instant shippedAt,
            Instant deliveredAt,
            String returnReason,
            Instant reminderWeekSentAt,
            Instant reminder48hSentAt) {
    }

    public record UpdateDispatchStatusCommand(
            @NotBlank(message = "Status is required") String status,
            String trackingNumber,
            String returnReason,
            String courier) {
    }

    // --------------------------------------------------------------- address

    public record AddressCaptureRequest(
            @NotEmpty(message = "Select at least one creator") List<UUID> creatorIds,
            UUID campaignId) {
    }

    public record AddressCaptureResult(int emailsSent, int skipped, List<String> warnings) {
    }

    /** What the creator sees on the public capture form. */
    public record AddressFormView(
            String creatorName,
            String brandName,
            boolean alreadyCaptured) {
    }

    public record SubmitAddressCommand(
            @NotBlank(message = "Your name is required") String recipientName,
            @NotBlank(message = "Address line 1 is required") String street,
            String street2,
            @NotBlank(message = "Town or city is required") String city,
            String county,
            @NotBlank(message = "Postcode is required") String postalCode,
            String country,
            String phone,
            @NotNull(message = "Consent is required") Boolean gdprConsent) {
    }

    public record AddressResponse(
            UUID creatorId,
            String recipientName,
            String street,
            String street2,
            String city,
            String county,
            String postalCode,
            String country,
            String phone,
            boolean gdprConsent,
            String consentSource,
            Instant requestedAt,
            Instant capturedAt) {
    }

    // ---------------------------------------------------------- brand orders

    public record CreateBrandOrderCommand(
            @NotBlank(message = "The brand contact email is required") String brandContactEmail,
            UUID campaignId,
            UUID giftingRunId,
            String productName,
            @NotEmpty(message = "Select at least one creator") List<UUID> creatorIds,
            String notes) {
    }

    public record BrandOrderResponse(
            UUID id,
            UUID campaignId,
            UUID giftingRunId,
            String brandContactEmail,
            String productName,
            int recipientCount,
            String notes,
            String status,
            Instant confirmedAt,
            String rejectedReason,
            Instant createdAt) {
    }
}
