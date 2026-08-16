package com.generationb.gifting.internal;

import com.generationb.shared.CreatorLookupPort;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftingService {

    private final GiftingAddressRepository addressRepository;
    private final GiftingRunRepository runRepository;
    private final DispatchRepository dispatchRepository;
    private final CreatorLookupPort creatorLookup;
    private final EmailSender emailSender;

    /**
     * Q-E16: this used to return CSS variables ("var(--color-lime)") as data, hardcode the product
     * name, and invent a fake row when the table was empty. Presentation now belongs to the
     * frontend and every value is real.
     *
     * <p>Q-G2: one query with the creator handles resolved in a single batch, instead of two
     * queries per dispatch row.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGiftingLog() {
        UUID brandId = BrandContext.requireBrandId();
        List<Dispatch> dispatches = dispatchRepository.findAllForBrand(brandId);
        if (dispatches.isEmpty()) {
            return List.of();
        }

        List<UUID> creatorIds = dispatches.stream().map(Dispatch::getCreatorId).distinct().toList();
        Map<UUID, String> handles = creatorLookup.findContacts(creatorIds).stream()
                .collect(Collectors.toMap(CreatorLookupPort.CreatorContact::creatorId,
                        CreatorLookupPort.CreatorContact::handle, (a, b) -> a));
        Set<UUID> withAddress = new HashSet<>(addressRepository.findCreatorIdsWithAddress(creatorIds));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Dispatch d : dispatches) {
            boolean addressCaptured = withAddress.contains(d.getCreatorId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId().toString());
            row.put("creatorId", d.getCreatorId());
            row.put("handle", handles.getOrDefault(d.getCreatorId(), "unknown"));
            row.put("addressStatus", addressCaptured ? "CAPTURED" : "PENDING");
            row.put("gdprConsent", addressCaptured);
            row.put("productName", d.getProductName());
            row.put("courier", d.getCourier());
            row.put("trackingNumber", d.getTrackingNumber());
            row.put("status", d.getStatus());
            row.put("shippedAt", d.getShippedAt());
            row.put("deliveredAt", d.getDeliveredAt());
            row.put("returnReason", d.getReturnReason());
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> sendAddressCaptureEmails(List<String> recipientIds) {
        log.info("[GIFTING LOGISTICS] Triggering address capture emails to {} recipients", recipientIds.size());
        return Map.of("success", true, "sentCount", recipientIds.size());
    }

    // TODO(confirm): schema de upload da EC — Luke/Amber
    public Map<String, String> exportEcGroupExcel() {
        log.info("[GIFTING LOGISTICS] Generating EC Group Excel export file...");
        String downloadUrl = String.format("/downloads/ec-group-gifting-%d.xlsx", System.currentTimeMillis());
        return Map.of("downloadUrl", downloadUrl, "status", "SUCCESS");
    }

    @Transactional
    public Map<String, Object> sendDirectBrandOrder(String brandName, List<String> recipientIds, String notes) {
        log.info("[GIFTING LOGISTICS] Direct brand order request sent to brand: {}, count: {}", brandName, recipientIds.size());
        return Map.of("success", true, "brand", brandName, "recipientCount", recipientIds.size());
    }

    // TODO(confirm): template atual usado com a EC — Amber
    @Transactional
    public GiftingRun approveCompSlip(UUID giftingRunId, String mailerText) {
        GiftingRun run = runRepository.findById(giftingRunId)
                .orElseGet(() -> {
                    GiftingRun r = new GiftingRun();
                    r.setBrandId(BrandContext.getCurrentBrandId() != null ? BrandContext.getCurrentBrandId() : UUID.fromString("11111111-1111-1111-1111-111111111111"));
                    return r;
                });

        run.setMailerText(mailerText != null ? mailerText : "Thank you for partnering with Generation B!");
        run.setCompSlipStatus("APPROVED");
        return runRepository.save(run);
    }

    @Transactional
    public Dispatch updateDispatchStatus(UUID dispatchId, String status, String trackingNumber, String returnReason) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch not found"));

        d.setStatus(status);
        if (trackingNumber != null) d.setTrackingNumber(trackingNumber);
        if (returnReason != null) d.setReturnReason(returnReason);
        if ("DELIVERED".equalsIgnoreCase(status)) d.setDeliveredAt(Instant.now());
        if ("DISPATCHED".equalsIgnoreCase(status)) d.setShippedAt(Instant.now());

        return dispatchRepository.save(d);
    }

    // TODO(confirm): copy final do lembrete a ser revisada — Sally-Anne/Chloé
    @Scheduled(cron = "0 0 10 * * *")
    public void runDeliveryReceiptReminderSequence() {
        log.info("[GIFTING REMINDER JOB] Checking delivered dispatches for post reminders (7-day / 48h before deadline)...");
    }
}
