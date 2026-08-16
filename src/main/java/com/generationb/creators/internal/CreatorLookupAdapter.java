package com.generationb.creators.internal;

import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The creators module's implementation of {@link CreatorLookupPort}.
 *
 * <p>Replaces the previous request/response-over-ApplicationEvent mechanism (Q-E3) and the
 * direct {@code internal} import from the gifting module (Q-E1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorLookupAdapter implements CreatorLookupPort {

    private static final DateTimeFormatter HUMAN_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneId.of("Europe/London"));

    private final CreatorRepository creatorRepository;
    private final CreatorSendHistoryRepository sendHistoryRepository;
    private final GlobalSuppressionRepository suppressionRepository;
    private final CreatorBrandLinkRepository brandLinkRepository;
    private final CreatorService creatorService;

    @Override
    @Transactional(readOnly = true)
    public Optional<CreatorContact> findContact(UUID creatorId) {
        return creatorRepository.findActiveById(creatorId).map(this::toContact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatorContact> findContacts(List<UUID> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return List.of();
        }
        return creatorRepository.findAllActiveByIds(creatorIds).stream().map(this::toContact).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findLastWorkedWith(UUID creatorId, UUID brandId) {
        if (creatorId == null || brandId == null) {
            return Optional.empty();
        }
        // Q-J3: ordered by sent_at, and formatted for a human rather than an ISO instant that
        // used to be pasted straight into customer-facing email copy.
        return sendHistoryRepository.findMostRecentForBrand(creatorId, brandId)
                .map(history -> HUMAN_DATE.format(history.getSentAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSuppressed(UUID creatorId) {
        if (creatorId == null) {
            return false;
        }
        if (suppressionRepository.existsByCreatorId(creatorId)) {
            return true;
        }
        return creatorRepository.findActiveById(creatorId)
                .map(c -> c.getEmail() != null && suppressionRepository.existsByEmailIgnoreCase(c.getEmail()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSuppressedByEmail(String email) {
        return email != null && !email.isBlank() && suppressionRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Override
    @Transactional
    public void recordSend(UUID creatorId, UUID brandId, UUID campaignId, String sendType, String productName) {
        if (creatorId == null || brandId == null) {
            return;
        }
        boolean duplicate = sendHistoryRepository.existsForOtherBrand(creatorId, brandId);

        CreatorSendHistory history = new CreatorSendHistory();
        history.setCreatorId(creatorId);
        history.setBrandId(brandId);
        history.setCampaignId(campaignId);
        history.setSendType(sendType != null ? sendType : "OUTREACH");
        history.setProductName(productName);
        history.setDuplicateFlag(duplicate);
        sendHistoryRepository.save(history);

        // Contacting a creator is an engagement, so the brand relationship advances.
        creatorService.linkToBrand(creatorId, brandId, CreatorBrandLink.CONTACTED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasWorkedWithOtherBrand(UUID creatorId, UUID brandId) {
        return creatorId != null && brandId != null
                && brandLinkRepository.existsOtherBrandEngagement(creatorId, brandId);
    }

    private CreatorContact toContact(Creator creator) {
        String name = creator.getName();
        String firstName = (name != null && !name.isBlank())
                ? name.trim().split("\\s+")[0]
                : creator.getHandle();
        return new CreatorContact(
                creator.getId(), creator.getEmail(), firstName, name, creator.getHandle());
    }
}
