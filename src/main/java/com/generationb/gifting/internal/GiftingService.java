package com.generationb.gifting.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.foundation.email.EmailSender;
import com.generationb.gifting.GiftingDtos.*;
import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gifting logistics: requirements #41 and #43–#48.
 *
 * <p>Everything here used to return {@code {"success": true}} without writing anything. The
 * rewrite makes each step leave a record: an address is requested before it is captured, a comp
 * slip is approved by a named person, a dispatch is only created for a creator who has an
 * address and is not excluded, and a refusal flags the creator.
 */
@Slf4j
@Service
@Audited
@RequiredArgsConstructor
public class GiftingService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    /** Long enough that the link cannot be guessed, short enough to survive email clients. */
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_VALID_DAYS = 30;

    private final GiftingAddressRepository addressRepository;
    private final GiftingRunRepository runRepository;
    private final DispatchRepository dispatchRepository;
    private final BrandOrderRepository brandOrderRepository;
    private final CreatorLookupPort creatorLookup;
    private final BrandLookupPort brandLookup;
    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // =====================================================================
    // Runs and comp slips (#44)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<RunResponse> listRuns() {
        List<GiftingRun> runs = runRepository.findAllScoped();
        return runs.stream()
                .map(run -> toRunResponse(run, dispatchRepository.findByGiftingRunId(run.getId()).size()))
                .toList();
    }

    @Transactional
    public RunResponse createRun(CreateRunCommand command) {
        GiftingRun run = new GiftingRun();
        run.setBrandId(BrandContext.requireBrandId());
        run.setName(command.name().trim());
        run.setCampaignId(command.campaignId());
        run.setProductName(command.productName());
        run.setMailerText(command.mailerText());
        run.setCompSlipStatus(GiftingRun.PENDING);
        return toRunResponse(runRepository.save(run), 0);
    }

    /**
     * Requirement #44: the comp slip wording is signed off before anything ships. Approval is
     * recorded against the user who gave it, so "who approved this?" has an answer.
     */
    @Transactional
    public RunResponse approveCompSlip(UUID runId, String mailerText) {
        GiftingRun run = requireRun(runId);

        if (mailerText != null && !mailerText.isBlank()) {
            run.setMailerText(mailerText.trim());
        }
        if (run.getMailerText() == null || run.getMailerText().isBlank()) {
            throw ApiException.badRequest("There is no comp slip wording to approve yet.");
        }

        run.setCompSlipStatus(GiftingRun.APPROVED);
        run.setApprovedBy(BrandContext.getCurrentUserId());
        run.setApprovedAt(Instant.now());

        GiftingRun saved = runRepository.save(run);
        return toRunResponse(saved, dispatchRepository.findByGiftingRunId(saved.getId()).size());
    }

    @Transactional
    public RunResponse rejectCompSlip(UUID runId) {
        GiftingRun run = requireRun(runId);
        run.setCompSlipStatus(GiftingRun.REJECTED);
        run.setApprovedBy(null);
        run.setApprovedAt(null);
        GiftingRun saved = runRepository.save(run);
        return toRunResponse(saved, dispatchRepository.findByGiftingRunId(saved.getId()).size());
    }

    // =====================================================================
    // Address capture (#41)
    // =====================================================================

    /**
     * Requirement #41: emails each creator a single-use link to a form that captures their
     * address and their consent to us holding it.
     */
    @Transactional
    public AddressCaptureResult requestAddresses(AddressCaptureRequest request) {
        UUID brandId = BrandContext.requireBrandId();
        List<String> warnings = new ArrayList<>();
        int sent = 0;
        int skipped = 0;

        for (CreatorLookupPort.CreatorContact contact
                : creatorLookup.findContacts(request.creatorIds())) {

            if (contact.email() == null || contact.email().isBlank()) {
                warnings.add("@" + contact.handle() + " has no email address on file.");
                skipped++;
                continue;
            }
            // Requirement #21 and #47 both bar a send here.
            if (creatorLookup.isSuppressed(contact.creatorId())) {
                warnings.add("@" + contact.handle() + " has opted out of contact.");
                skipped++;
                continue;
            }
            if (creatorLookup.isGiftingExcluded(contact.creatorId())) {
                warnings.add("@" + contact.handle() + " is excluded from gifting.");
                skipped++;
                continue;
            }

            GiftingAddress address = addressRepository.findByCreatorId(contact.creatorId())
                    .orElseGet(() -> {
                        GiftingAddress created = new GiftingAddress();
                        created.setCreatorId(contact.creatorId());
                        return created;
                    });

            if (address.isCaptured()) {
                warnings.add("@" + contact.handle() + " has already given us an address.");
                skipped++;
                continue;
            }

            address.setCampaignId(request.campaignId());
            address.setBrandId(brandId);
            address.setCaptureToken(newToken());
            address.setTokenExpiresAt(Instant.now().plus(TOKEN_VALID_DAYS, ChronoUnit.DAYS));
            address.setRequestedAt(Instant.now());
            addressRepository.save(address);

            emailSender.sendAddressCaptureRequest(contact.email(), contact.firstName(),
                    frontendUrl + "/gifting/address/" + address.getCaptureToken());
            sent++;
        }

        log.info("Address capture: {} email(s) sent, {} skipped", sent, skipped);
        return new AddressCaptureResult(sent, skipped, warnings);
    }

    /** The public form's read side. The token is the only credential the creator has. */
    @Transactional(readOnly = true)
    public AddressFormView viewAddressForm(String token) {
        GiftingAddress address = requireToken(token);
        String name = creatorLookup.findContact(address.getCreatorId())
                .map(CreatorLookupPort.CreatorContact::firstName)
                .orElse("there");
        // The brand is read from the record, not hard-coded: the consent wording on the form
        // names it, so it has to be the brand that actually asked.
        String brandName = address.getBrandId() == null
                ? "us"
                : brandLookup.findBrandName(address.getBrandId()).orElse("us");
        return new AddressFormView(name, brandName, address.isCaptured());
    }

    /** The public form's write side. */
    @Transactional
    public void submitAddress(String token, SubmitAddressCommand command) {
        GiftingAddress address = requireToken(token);

        if (!Boolean.TRUE.equals(command.gdprConsent())) {
            throw ApiException.badRequest(
                    "We can only store your address if you agree to us holding it.");
        }

        address.setRecipientName(command.recipientName().trim());
        address.setStreet(command.street().trim());
        address.setStreet2(command.street2());
        address.setCity(command.city().trim());
        address.setCounty(command.county());
        address.setPostalCode(command.postalCode().trim().toUpperCase());
        address.setCountry(command.country() == null || command.country().isBlank()
                ? "UK" : command.country().trim());
        address.setPhone(command.phone());
        address.setGdprConsentFlag(true);
        address.setConsentSource(GiftingAddress.SOURCE_SELF_SERVE);
        address.setConsentedAt(Instant.now());
        address.setCapturedAt(Instant.now());
        // Single use: the link stops working once the form has been submitted.
        address.setCaptureToken(null);
        address.setTokenExpiresAt(null);

        addressRepository.save(address);
        log.info("Address captured for creator {}", address.getCreatorId());
    }

    @Transactional(readOnly = true)
    public Optional<AddressResponse> getAddress(UUID creatorId) {
        return addressRepository.findByCreatorId(creatorId).map(this::toAddressResponse);
    }

    // =====================================================================
    // Dispatches (#45, #47, #48)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<DispatchResponse> getGiftingLog() {
        UUID brandId = BrandContext.requireBrandId();
        List<Dispatch> dispatches = dispatchRepository.findAllForBrand(brandId);
        return decorate(dispatches);
    }

    /**
     * Requirement #45: creates the dispatch rows. A creator with no captured address, or one
     * excluded after a previous refusal, is skipped and reported rather than silently included —
     * a parcel with no address is a wasted parcel.
     */
    @Transactional
    public DispatchCreationResult createDispatches(CreateDispatchesCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        GiftingRun run = command.giftingRunId() != null ? requireRun(command.giftingRunId()) : null;
        if (run != null && !run.isApproved()) {
            throw ApiException.unprocessable(
                    "The comp slip for this run has not been approved yet.");
        }

        List<UUID> creatorIds = command.creatorIds().stream().distinct().toList();
        Set<UUID> withAddress = new HashSet<>(addressRepository.findCreatorIdsWithAddress(creatorIds));
        Map<UUID, CreatorLookupPort.CreatorContact> contacts = creatorLookup.findContacts(creatorIds)
                .stream().collect(Collectors.toMap(
                        CreatorLookupPort.CreatorContact::creatorId, c -> c, (a, b) -> a));

        Set<UUID> alreadyOnRun = run == null ? Set.of()
                : dispatchRepository.findByGiftingRunId(run.getId()).stream()
                        .map(Dispatch::getCreatorId).collect(Collectors.toSet());

        List<String> warnings = new ArrayList<>();
        List<Dispatch> created = new ArrayList<>();
        int noAddress = 0;
        int excluded = 0;
        int duplicate = 0;

        String productName = command.productName() != null && !command.productName().isBlank()
                ? command.productName()
                : (run != null ? run.getProductName() : null);

        for (UUID creatorId : creatorIds) {
            String label = "@" + Optional.ofNullable(contacts.get(creatorId))
                    .map(CreatorLookupPort.CreatorContact::handle).orElse(creatorId.toString());

            if (creatorLookup.isGiftingExcluded(creatorId)) {
                warnings.add(label + " is excluded from gifting after a previous refusal.");
                excluded++;
                continue;
            }
            if (!withAddress.contains(creatorId)) {
                warnings.add(label + " has no confirmed address yet.");
                noAddress++;
                continue;
            }
            if (alreadyOnRun.contains(creatorId)) {
                warnings.add(label + " is already on this run.");
                duplicate++;
                continue;
            }

            Dispatch dispatch = new Dispatch();
            dispatch.setBrandId(brandId);
            dispatch.setGiftingRunId(run == null ? null : run.getId());
            dispatch.setCreatorId(creatorId);
            dispatch.setProductName(productName);
            dispatch.setSku(command.sku());
            dispatch.setPackagingNotes(command.packagingNotes());
            dispatch.setPlannedDispatchDate(command.plannedDispatchDate());
            dispatch.setContentDeadline(command.contentDeadline());
            if (command.courier() != null && !command.courier().isBlank()) {
                dispatch.setCourier(command.courier());
            }
            dispatch.setStatus(Dispatch.READY);
            created.add(dispatchRepository.save(dispatch));

            // Requirement #19: the cross-brand history is what makes duplicate-send warnings work.
            creatorLookup.recordSend(creatorId, brandId,
                    run == null ? null : run.getCampaignId(), "GIFT", productName);
        }

        log.info("Created {} dispatch(es); skipped {} without an address, {} excluded, {} duplicate",
                created.size(), noAddress, excluded, duplicate);

        return new DispatchCreationResult(created.size(), noAddress, excluded, duplicate,
                warnings, decorate(created));
    }

    /**
     * Requirement #47: a refusal or a return excludes the creator from future gifting. The flag
     * is set here rather than left to someone remembering to tick a box.
     */
    @Transactional
    public DispatchResponse updateDispatchStatus(UUID dispatchId, UpdateDispatchStatusCommand command) {
        UUID brandId = BrandContext.requireBrandId();
        Dispatch dispatch = dispatchRepository.findScopedById(dispatchId, brandId)
                .orElseThrow(() -> ApiException.notFound("Dispatch"));

        String status = command.status().trim().toUpperCase();
        if (!Dispatch.isValidStatus(status)) {
            throw ApiException.badRequest("Unknown dispatch status: " + command.status());
        }

        dispatch.setStatus(status);
        if (command.trackingNumber() != null) {
            dispatch.setTrackingNumber(command.trackingNumber());
        }
        if (command.courier() != null && !command.courier().isBlank()) {
            dispatch.setCourier(command.courier());
        }
        if (command.returnReason() != null) {
            dispatch.setReturnReason(command.returnReason());
        }

        switch (status) {
            case Dispatch.DISPATCHED -> {
                if (dispatch.getShippedAt() == null) {
                    dispatch.setShippedAt(Instant.now());
                }
            }
            case Dispatch.DELIVERED -> {
                if (dispatch.getDeliveredAt() == null) {
                    dispatch.setDeliveredAt(Instant.now());
                }
            }
            case Dispatch.RETURNED, Dispatch.DECLINED -> {
                String reason = command.returnReason() != null && !command.returnReason().isBlank()
                        ? command.returnReason()
                        : (Dispatch.DECLINED.equals(status) ? "Declined the gift" : "Parcel returned");
                creatorLookup.flagGiftingExclusion(dispatch.getCreatorId(), reason);
            }
            default -> { }
        }

        Dispatch saved = dispatchRepository.save(dispatch);
        return decorate(List.of(saved)).get(0);
    }

    // =====================================================================
    // Direct-from-brand orders (#43)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<BrandOrderResponse> listBrandOrders() {
        return brandOrderRepository.findAllScoped().stream().map(this::toBrandOrderResponse).toList();
    }

    /**
     * Requirement #43: emails the brand's own contact asking them to ship, with a tokenised link
     * they use to confirm. Nothing is marked as sent until they click it.
     */
    @Transactional
    public BrandOrderResponse createBrandOrder(CreateBrandOrderCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        BrandOrder order = new BrandOrder();
        order.setBrandId(brandId);
        order.setCampaignId(command.campaignId());
        order.setGiftingRunId(command.giftingRunId());
        order.setBrandContactEmail(command.brandContactEmail().trim());
        order.setProductName(command.productName());
        order.setRecipientCount(command.creatorIds().size());
        order.setNotes(command.notes());
        order.setStatus(BrandOrder.REQUESTED);
        order.setConfirmToken(newToken());

        BrandOrder saved = brandOrderRepository.save(order);

        String brandName = brandLookup.findBrandName(brandId).orElse("the brand");
        emailSender.sendBrandOrderRequest(saved.getBrandContactEmail(), brandName,
                saved.getRecipientCount(),
                frontendUrl + "/gifting/brand-order/" + saved.getConfirmToken(),
                saved.getNotes());

        return toBrandOrderResponse(saved);
    }

    /** Public: the brand contact confirming they have shipped. */
    @Transactional
    public void confirmBrandOrder(String token) {
        BrandOrder order = brandOrderRepository.findByConfirmToken(token)
                .orElseThrow(() -> ApiException.notFound("Order request"));

        if (BrandOrder.CONFIRMED.equals(order.getStatus())) {
            // The token is deliberately left live: the brand contact clicking their emailed link
            // a second time should see the confirmation again, not a 404. Re-confirming is a
            // no-op, so there is nothing to protect by burning it.
            return;
        }
        order.setStatus(BrandOrder.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        brandOrderRepository.save(order);

        // Everything on the run has now physically left the brand.
        if (order.getGiftingRunId() != null) {
            for (Dispatch dispatch : dispatchRepository.findByGiftingRunId(order.getGiftingRunId())) {
                if (Dispatch.READY.equals(dispatch.getStatus())) {
                    dispatch.setStatus(Dispatch.DISPATCHED);
                    dispatch.setShippedAt(Instant.now());
                    dispatchRepository.save(dispatch);
                }
            }
        }
        log.info("Brand order {} confirmed", order.getId());
    }

    // =====================================================================
    // Reminder sequence (#46)
    // =====================================================================

    /**
     * Requirement #46: nudges creators whose content deadline is a week away, then again 48
     * hours before. Each nudge is stamped on the dispatch so a restart cannot re-send it.
     */
    @Transactional
    public int runReminderSequence() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        sent += remind(dispatchRepository.findDeliveredWithDeadlineBetween(
                        today.plusDays(7), today.plusDays(7)),
                true, "in a week");
        sent += remind(dispatchRepository.findDeliveredWithDeadlineBetween(
                        today.plusDays(2), today.plusDays(2)),
                false, "in 48 hours");

        if (sent > 0) {
            log.info("Gifting reminder sequence sent {} email(s)", sent);
        }
        return sent;
    }

    private int remind(List<Dispatch> dispatches, boolean weekStage, String deadlineLabel) {
        int sent = 0;
        for (Dispatch dispatch : dispatches) {
            if (weekStage ? dispatch.getReminderWeekSentAt() != null
                          : dispatch.getReminder48hSentAt() != null) {
                continue;
            }
            Optional<CreatorLookupPort.CreatorContact> contact =
                    creatorLookup.findContact(dispatch.getCreatorId());

            boolean delivered = contact.isPresent()
                    && contact.get().email() != null && !contact.get().email().isBlank()
                    && !creatorLookup.isSuppressed(dispatch.getCreatorId());

            if (delivered) {
                emailSender.sendGiftReminder(contact.get().email(), contact.get().firstName(),
                        dispatch.getProductName() == null ? "your gift" : dispatch.getProductName(),
                        deadlineLabel);
                sent++;
            }
            // Stamp either way: an unreachable creator must not be retried every day.
            if (weekStage) {
                dispatch.setReminderWeekSentAt(Instant.now());
            } else {
                dispatch.setReminder48hSentAt(Instant.now());
            }
            dispatchRepository.save(dispatch);
        }
        return sent;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private GiftingRun requireRun(UUID runId) {
        return runRepository.findScopedById(runId)
                .orElseThrow(() -> ApiException.notFound("Gifting run"));
    }

    private GiftingAddress requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("Address link");
        }
        GiftingAddress address = addressRepository.findByCaptureToken(token)
                .orElseThrow(() -> ApiException.notFound("Address link"));

        if (address.getTokenExpiresAt() != null && address.getTokenExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unprocessable("This link has expired. Ask us for a new one.");
        }
        return address;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /** Q-G2: handles and address status resolved in two queries, not two per row. */
    private List<DispatchResponse> decorate(List<Dispatch> dispatches) {
        if (dispatches.isEmpty()) {
            return List.of();
        }
        List<UUID> creatorIds = dispatches.stream().map(Dispatch::getCreatorId).distinct().toList();
        Map<UUID, CreatorLookupPort.CreatorContact> contacts = creatorLookup.findContacts(creatorIds)
                .stream().collect(Collectors.toMap(
                        CreatorLookupPort.CreatorContact::creatorId, c -> c, (a, b) -> a));
        Set<UUID> withAddress = new HashSet<>(addressRepository.findCreatorIdsWithAddress(creatorIds));

        return dispatches.stream().map(d -> {
            CreatorLookupPort.CreatorContact contact = contacts.get(d.getCreatorId());
            boolean captured = withAddress.contains(d.getCreatorId());
            return new DispatchResponse(
                    d.getId(), d.getGiftingRunId(), d.getCreatorId(),
                    contact == null ? "unknown" : contact.handle(),
                    contact == null ? null : contact.fullName(),
                    d.getProductName(), d.getSku(), d.getCourier(), d.getTrackingNumber(),
                    d.getStatus(), captured ? "CAPTURED" : "PENDING", captured,
                    d.getPlannedDispatchDate(), d.getContentDeadline(),
                    d.getShippedAt(), d.getDeliveredAt(), d.getReturnReason(),
                    d.getReminderWeekSentAt(), d.getReminder48hSentAt());
        }).toList();
    }

    private RunResponse toRunResponse(GiftingRun run, int dispatchCount) {
        return new RunResponse(run.getId(), run.getName(), run.getCampaignId(),
                run.getProductName(), run.getMailerText(), run.getCompSlipStatus(),
                run.getApprovedBy(), run.getApprovedAt(), dispatchCount, run.getCreatedAt());
    }

    private AddressResponse toAddressResponse(GiftingAddress a) {
        return new AddressResponse(a.getCreatorId(), a.getRecipientName(), a.getStreet(),
                a.getStreet2(), a.getCity(), a.getCounty(), a.getPostalCode(), a.getCountry(),
                a.getPhone(), a.isGdprConsentFlag(), a.getConsentSource(),
                a.getRequestedAt(), a.getCapturedAt());
    }

    private BrandOrderResponse toBrandOrderResponse(BrandOrder o) {
        return new BrandOrderResponse(o.getId(), o.getCampaignId(), o.getGiftingRunId(),
                o.getBrandContactEmail(), o.getProductName(), o.getRecipientCount(), o.getNotes(),
                o.getStatus(), o.getConfirmedAt(), o.getRejectedReason(), o.getCreatedAt());
    }
}
