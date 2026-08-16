package com.generationb.creators.internal;

import com.generationb.creators.*;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.Audited;
import com.generationb.foundation.BrandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creator database + matching.
 *
 * <p>Rewritten against the answered review: creators are global (Q-C6), every list is paginated
 * and filtered in the database (Q-G1), suppression is enforced (Q-E13/Q-I1), send history is
 * actually written (#19), and nothing falls back to a hardcoded demo tenant (Q-C5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Audited
public class CreatorService {

    private static final DateTimeFormatter HUMAN_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Europe/London"));

    private final CreatorRepository creatorRepository;
    private final CreatorBrandLinkRepository brandLinkRepository;
    private final CreatorNoteRepository noteRepository;
    private final CreatorNoteRevisionRepository noteRevisionRepository;
    private final CreatorSendHistoryRepository sendHistoryRepository;
    private final GlobalSuppressionRepository suppressionRepository;
    private final ContentStyleTagRepository tagRepository;
    private final CreatorStyleTagLinkRepository tagLinkRepository;

    // ------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<CreatorResponse> search(CreatorSearchCriteria criteria, Pageable pageable) {
        Page<Creator> page = creatorRepository.search(
                criteria.q(), criteria.platform(), criteria.location(), criteria.niche(),
                criteria.minFollowers(), criteria.maxFollowers(), criteria.minEr(),
                criteria.minUkAudience(), criteria.optInStatus(), criteria.tagId(), pageable);
        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public CreatorResponse getCreator(UUID id) {
        Creator creator = requireCreator(id);
        return toDetail(creator);
    }

    @Transactional(readOnly = true)
    public Page<CreatorResponse> pendingRegistrations(Pageable pageable) {
        return creatorRepository.findByOptInStatus("PENDING_REVIEW", pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filterOptions() {
        UUID brandId = BrandContext.requireBrandId();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("niches", creatorRepository.findDistinctNiches());
        options.put("locations", creatorRepository.findDistinctLocations());
        options.put("platforms", List.of("INSTAGRAM", "TIKTOK", "YOUTUBE"));
        options.put("followerBands", List.of("Under 10K", "10K-50K", "50K-100K", "100K-250K", "250K+"));
        options.put("tags", tagRepository.findAllForBrand(brandId).stream()
                .map(t -> new StyleTagResponse(t.getId(), t.getName(), t.getCategory(),
                        tagRepository.countCreators(t.getId())))
                .toList());
        options.put("totalCreators", creatorRepository.countActive());
        return options;
    }

    // ------------------------------------------------------------- mutation

    @Transactional
    public CreatorResponse createCreator(CreateCreatorCommand command) {
        UUID brandId = BrandContext.requireBrandId();

        String handle = normaliseHandle(command.handle());
        creatorRepository.findByHandleIgnoreCase(handle).ifPresent(existing -> {
            throw ApiException.conflict("A creator with the handle @" + handle + " already exists");
        });

        Creator creator = new Creator();
        creator.setName(command.name());
        creator.setHandle(handle);
        creator.setEmail(trimToNull(command.email()));
        creator.setPhone(trimToNull(command.phone()));
        creator.setPrimaryPlatform(upperOrDefault(command.primaryPlatform(), "INSTAGRAM"));
        creator.setTiktokHandle(trimToNull(command.tiktokHandle()));
        creator.setYoutubeHandle(trimToNull(command.youtubeHandle()));
        creator.setFollowersCount(command.followersCount() != null ? command.followersCount() : 0);
        creator.setErPercentage(command.erPercentage() != null ? command.erPercentage() : BigDecimal.ZERO);
        creator.setLocation(trimToNull(command.location()));
        creator.setNiche(trimToNull(command.niche()));
        creator.setBio(trimToNull(command.bio()));
        creator.setPortfolioUrl(trimToNull(command.portfolioUrl()));
        creator.setOptInStatus("APPROVED");

        Creator saved = creatorRepository.save(creator);
        linkToBrand(saved.getId(), brandId, CreatorBrandLink.PROSPECT);
        applyTags(saved.getId(), command.tagIds(), brandId);

        return toDetail(saved);
    }

    @Transactional
    public CreatorResponse updateCreator(UUID id, UpdateCreatorCommand command) {
        UUID brandId = BrandContext.requireBrandId();
        Creator creator = requireCreator(id);

        if (command.handle() != null && !command.handle().isBlank()) {
            String handle = normaliseHandle(command.handle());
            creatorRepository.findByHandleIgnoreCase(handle).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw ApiException.conflict("A creator with the handle @" + handle + " already exists");
                }
            });
            creator.setHandle(handle);
        }

        // Only overwrite what was actually sent (Q-E10).
        if (command.name() != null) creator.setName(command.name());
        if (command.email() != null) creator.setEmail(trimToNull(command.email()));
        if (command.phone() != null) creator.setPhone(trimToNull(command.phone()));
        if (command.primaryPlatform() != null) creator.setPrimaryPlatform(command.primaryPlatform().toUpperCase());
        if (command.tiktokHandle() != null) creator.setTiktokHandle(trimToNull(command.tiktokHandle()));
        if (command.youtubeHandle() != null) creator.setYoutubeHandle(trimToNull(command.youtubeHandle()));
        if (command.followersCount() != null) creator.setFollowersCount(command.followersCount());
        if (command.erPercentage() != null) creator.setErPercentage(command.erPercentage());
        if (command.location() != null) creator.setLocation(trimToNull(command.location()));
        if (command.niche() != null) creator.setNiche(trimToNull(command.niche()));
        if (command.bio() != null) creator.setBio(trimToNull(command.bio()));
        if (command.portfolioUrl() != null) creator.setPortfolioUrl(trimToNull(command.portfolioUrl()));
        if (command.optInStatus() != null) creator.setOptInStatus(command.optInStatus());

        Creator saved = creatorRepository.save(creator);
        if (command.tagIds() != null) {
            tagLinkRepository.deleteByCreatorId(id);
            applyTags(id, command.tagIds(), brandId);
        }
        return toDetail(saved);
    }

    @Transactional
    public void deleteCreator(UUID id) {
        Creator creator = requireCreator(id);
        creator.setDeletedAt(Instant.now());
        creatorRepository.save(creator);
    }

    /** Requirement #20: approve or reject a self-registration. */
    @Transactional
    public CreatorResponse reviewRegistration(UUID id, boolean approve) {
        UUID brandId = BrandContext.requireBrandId();
        Creator creator = requireCreator(id);
        creator.setOptInStatus(approve ? "APPROVED" : "REJECTED");
        Creator saved = creatorRepository.save(creator);
        if (approve) {
            linkToBrand(id, brandId, CreatorBrandLink.PROSPECT);
        }
        return toDetail(saved);
    }

    /**
     * Requirement #20, public endpoint. Every field the form collects is now stored, and consent
     * is recorded by the caller (PublicCreatorController) rather than discarded.
     */
    @Transactional
    public Creator registerFromPublicForm(RegisterCreatorCommand command) {
        String handle = normaliseHandle(
                command.instagram() != null && !command.instagram().isBlank()
                        ? command.instagram()
                        : command.fullName());

        // Q-B15: create-only. A public endpoint must never let a stranger overwrite an existing
        // creator's contact details by posting their handle.
        Optional<Creator> existing = creatorRepository.findByHandleIgnoreCase(handle);
        if (existing.isPresent()) {
            Creator found = existing.get();
            if ("PENDING_REVIEW".equals(found.getOptInStatus())) {
                return found; // idempotent re-submit of a pending application
            }
            throw ApiException.conflict("That handle is already registered. Please contact the team.");
        }

        Creator creator = new Creator();
        creator.setName(command.fullName());
        creator.setHandle(handle);
        creator.setEmail(trimToNull(command.email()));
        creator.setPrimaryPlatform(upperOrDefault(command.platform(), "INSTAGRAM"));
        creator.setTiktokHandle(trimToNull(command.tiktok()));
        creator.setYoutubeHandle(trimToNull(command.youtube()));
        creator.setFollowerBand(trimToNull(command.followerBand()));
        creator.setNiche(trimToNull(command.niche()));
        creator.setBio(trimToNull(command.bio()));
        creator.setPortfolioUrl(trimToNull(command.portfolio()));
        creator.setOptInStatus("PENDING_REVIEW");
        creator.setOptInStep(5);
        if (command.er() != null && !command.er().isBlank()) {
            try {
                creator.setErPercentage(new BigDecimal(command.er().replace("%", "").trim()));
            } catch (NumberFormatException ignored) {
                // A creator typing "about 4" should not fail the whole registration.
            }
        }

        return creatorRepository.save(creator);
    }

    // ------------------------------------------------------------ CSV import

    public record ImportResult(int imported, int skipped, List<String> errors) {
    }

    /**
     * Requirement #22. Still row-oriented (the frontend parses the file), but now validated,
     * deduplicated by handle AND email, and reports why rows were rejected.
     */
    @Transactional
    public ImportResult importCreators(List<Map<String, String>> rows) {
        UUID brandId = BrandContext.requireBrandId();
        if (rows == null || rows.isEmpty()) {
            return new ImportResult(0, 0, List.of("No rows supplied"));
        }
        if (rows.size() > 5000) {
            throw ApiException.badRequest("Import is limited to 5000 rows per request");
        }

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String rawHandle = firstNonBlank(row.get("handle"), row.get("Handle"), row.get("instagram"));
            String email = firstNonBlank(row.get("email"), row.get("Email"));

            if (rawHandle == null || rawHandle.isBlank()) {
                skipped++;
                errors.add("Row " + (i + 2) + ": missing handle");
                continue;
            }

            String handle = normaliseHandle(rawHandle);
            if (creatorRepository.findByHandleIgnoreCase(handle).isPresent()) {
                skipped++;
                continue;
            }
            if (email != null && !email.isBlank()
                    && creatorRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
                skipped++;
                continue;
            }

            Creator creator = new Creator();
            creator.setHandle(handle);
            creator.setName(firstNonBlank(row.get("name"), row.get("Name"), handle));
            creator.setEmail(trimToNull(email));
            creator.setLocation(trimToNull(firstNonBlank(row.get("location"), row.get("Location"))));
            creator.setNiche(trimToNull(firstNonBlank(row.get("niche"), row.get("Niche"))));
            creator.setPrimaryPlatform(upperOrDefault(
                    firstNonBlank(row.get("platform"), row.get("Platform")), "INSTAGRAM"));
            creator.setFollowersCount(parseInt(firstNonBlank(row.get("followers"), row.get("followersCount"))));
            creator.setErPercentage(parseDecimal(firstNonBlank(row.get("er"), row.get("erPercentage"))));
            creator.setOptInStatus("APPROVED");

            Creator saved = creatorRepository.save(creator);
            linkToBrand(saved.getId(), brandId, CreatorBrandLink.PROSPECT);
            imported++;
        }

        return new ImportResult(imported, skipped, errors);
    }

    // ----------------------------------------------------------------- notes

    @Transactional
    public CreatorNoteResponse addNote(UUID creatorId, String noteText, boolean confidential) {
        UUID brandId = BrandContext.requireBrandId();
        requireCreator(creatorId);
        if (noteText == null || noteText.isBlank()) {
            throw ApiException.badRequest("Note text is required");
        }

        CreatorNote note = new CreatorNote();
        note.setCreatorId(creatorId);
        note.setBrandId(brandId);
        note.setAuthorId(BrandContext.getCurrentUserId());
        note.setNoteText(noteText.trim());
        note.setConfidential(confidential);
        return toNoteResponse(noteRepository.save(note));
    }

    /** Requirement #18 asks for edit history, so the previous text is kept on every edit. */
    @Transactional
    public CreatorNoteResponse updateNote(UUID noteId, String noteText) {
        UUID brandId = BrandContext.requireBrandId();
        CreatorNote note = noteRepository.findScopedById(noteId, brandId)
                .orElseThrow(() -> ApiException.notFound("Note"));
        if (noteText == null || noteText.isBlank()) {
            throw ApiException.badRequest("Note text is required");
        }
        noteRevisionRepository.save(
                CreatorNoteRevision.of(noteId, note.getNoteText(), BrandContext.getCurrentUserId()));
        note.setNoteText(noteText.trim());
        return toNoteResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(UUID noteId) {
        UUID brandId = BrandContext.requireBrandId();
        CreatorNote note = noteRepository.findScopedById(noteId, brandId)
                .orElseThrow(() -> ApiException.notFound("Note"));
        note.setDeletedAt(Instant.now());
        noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public List<CreatorNoteResponse> getNotes(UUID creatorId) {
        UUID brandId = BrandContext.requireBrandId();
        boolean isAdminOrDirector = isAdminOrDirector();
        return noteRepository.findVisibleNotes(creatorId, brandId).stream()
                // Confidential notes are visible to their author, plus admins and directors.
                .filter(n -> !n.isConfidential()
                        || isAdminOrDirector
                        || Objects.equals(n.getAuthorId(), BrandContext.getCurrentUserId()))
                .map(this::toNoteResponse)
                .toList();
    }

    // ---------------------------------------------------------- suppression

    /** Requirement #21: opt-out suppresses across every brand. */
    @Transactional
    public void suppress(String email, String handle, UUID creatorId, String reason, String source) {
        GlobalSuppression suppression = new GlobalSuppression();
        suppression.setEmail(trimToNull(email));
        suppression.setHandle(handle == null ? null : normaliseHandle(handle));
        suppression.setReason(reason != null ? reason : "Creator opt-out request");
        suppression.setSource(source != null ? source : "MANUAL");

        UUID resolved = creatorId;
        if (resolved == null && email != null) {
            resolved = creatorRepository.findByEmailIgnoreCase(email.trim())
                    .map(Creator::getId).orElse(null);
        }
        if (resolved == null && handle != null) {
            resolved = creatorRepository.findByHandleIgnoreCase(normaliseHandle(handle))
                    .map(Creator::getId).orElse(null);
        }
        suppression.setCreatorId(resolved);
        suppressionRepository.save(suppression);
        log.info("Suppression recorded source={} creatorId={}", suppression.getSource(), resolved);
    }

    @Transactional(readOnly = true)
    public Page<GlobalSuppression> listSuppressions(Pageable pageable) {
        return suppressionRepository.findAllOrdered(pageable);
    }

    // ------------------------------------------------------------ anonymise

    /** Q-I3: right to erasure, implemented as anonymisation so the suppression entry survives. */
    @Transactional
    public void anonymise(UUID creatorId) {
        Creator creator = requireCreator(creatorId);
        String priorEmail = creator.getEmail();

        creator.setName("Removed creator");
        creator.setEmail(null);
        creator.setPhone(null);
        creator.setBio(null);
        creator.setPortfolioUrl(null);
        creator.setTiktokHandle(null);
        creator.setYoutubeHandle(null);
        creator.setHandle("removed-" + creator.getId().toString().substring(0, 8));
        creator.setAnonymisedAt(Instant.now());
        creator.setOptInStatus("REJECTED");
        creator.setDeletedAt(Instant.now());
        creatorRepository.save(creator);

        // Keep them off every future list.
        suppress(priorEmail, null, creatorId, "Right to erasure exercised", "ERASURE");
        log.info("Creator {} anonymised under right to erasure", creatorId);
    }

    // ------------------------------------------------------------- helpers

    private Creator requireCreator(UUID id) {
        return creatorRepository.findActiveById(id)
                .orElseThrow(() -> ApiException.notFound("Creator"));
    }

    void linkToBrand(UUID creatorId, UUID brandId, String status) {
        brandLinkRepository.findByCreatorIdAndBrandId(creatorId, brandId)
                .ifPresentOrElse(link -> {
                    link.setLastEngagedAt(Instant.now());
                    if (CreatorBrandLink.WORKED_WITH.equals(status)) {
                        link.setRelationshipStatus(status);
                    }
                    brandLinkRepository.save(link);
                }, () -> brandLinkRepository.save(CreatorBrandLink.of(creatorId, brandId, status)));
    }

    private void applyTags(UUID creatorId, List<UUID> tagIds, UUID brandId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (UUID tagId : tagIds) {
            tagRepository.findScopedById(tagId, brandId).ifPresent(tag ->
                    tagLinkRepository.save(new CreatorStyleTagLink(creatorId, tag.getId())));
        }
    }

    private boolean isAdminOrDirector() {
        String role = BrandContext.getCurrentRole();
        return "ADMIN".equals(role) || "DIRECTOR".equals(role);
    }

    private CreatorResponse toSummary(Creator creator) {
        return buildResponse(creator, false);
    }

    private CreatorResponse toDetail(Creator creator) {
        return buildResponse(creator, true);
    }

    private CreatorResponse buildResponse(Creator creator, boolean withRelations) {
        UUID brandId = BrandContext.getCurrentBrandId();

        List<String> tags = List.of();
        List<CreatorResponse.BrandEngagement> engagements = List.of();
        if (withRelations) {
            List<UUID> tagIds = tagLinkRepository.findByCreatorId(creator.getId()).stream()
                    .map(CreatorStyleTagLink::getTagId).toList();
            tags = tagIds.isEmpty() ? List.of()
                    : tagRepository.findAllById(tagIds).stream().map(ContentStyleTag::getName).toList();
            engagements = brandLinkRepository.findByCreatorId(creator.getId()).stream()
                    .map(l -> new CreatorResponse.BrandEngagement(
                            l.getBrandId(), null, l.getRelationshipStatus(), l.getLastEngagedAt()))
                    .toList();
        }

        boolean workedWithOther = brandId != null
                && brandLinkRepository.existsOtherBrandEngagement(creator.getId(), brandId);
        boolean suppressed = suppressionRepository.isSuppressed(creator.getId(), creator.getEmail());

        String lastContact = brandId == null ? null
                : sendHistoryRepository.findMostRecentForBrand(creator.getId(), brandId)
                    .map(h -> HUMAN_DATE.format(h.getSentAt()))
                    .orElse("Never contacted");

        return new CreatorResponse(
                creator.getId(),
                creator.getName(),
                creator.getHandle(),
                creator.getEmail(),
                creator.getPhone(),
                creator.getPrimaryPlatform(),
                platformsOf(creator),
                creator.getTiktokHandle(),
                creator.getYoutubeHandle(),
                creator.getFollowersCount(),
                formatFollowers(creator.getFollowersCount()),
                creator.resolvedFollowerBand(),
                creator.getErPercentage(),
                creator.getLocation(),
                creator.getNiche(),
                creator.getBio(),
                creator.getPortfolioUrl(),
                creator.getUkAudiencePct(),
                creator.getAudienceAgeBand(),
                creator.getAudienceGenderSplit(),
                creator.getQualityBand(),
                creator.getOptInStatus(),
                tags,
                engagements,
                workedWithOther,
                suppressed,
                lastContact,
                creator.getCreatedAt()
        );
    }

    private List<String> platformsOf(Creator creator) {
        List<String> platforms = new ArrayList<>();
        if (creator.getPrimaryPlatform() != null) {
            platforms.add(creator.getPrimaryPlatform().toLowerCase());
        }
        if (creator.getTiktokHandle() != null && !platforms.contains("tiktok")) {
            platforms.add("tiktok");
        }
        if (creator.getYoutubeHandle() != null && !platforms.contains("youtube")) {
            platforms.add("youtube");
        }
        return platforms;
    }

    private CreatorNoteResponse toNoteResponse(CreatorNote note) {
        return new CreatorNoteResponse(
                note.getId(),
                note.getCreatorId(),
                note.getAuthorId(),
                null,
                note.getNoteText(),
                note.isConfidential(),
                (int) noteRevisionRepository.countByNoteId(note.getId()),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    static String formatFollowers(Integer count) {
        if (count == null) return "0";
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0).replace(".0M", "M");
        if (count >= 1_000) return String.format("%.0fK", count / 1_000.0);
        return String.valueOf(count);
    }

    static String normaliseHandle(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().replaceFirst("^@", "").toLowerCase();
        cleaned = cleaned.replaceAll("[^a-z0-9._-]", "");
        return cleaned.isBlank() ? "creator-" + UUID.randomUUID().toString().substring(0, 8) : cleaned;
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String upperOrDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim().toUpperCase();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
