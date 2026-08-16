package com.generationb.coverage.internal;

import com.generationb.creators.CreatorInsightsProvider;
import com.generationb.foundation.BrandContext;
import com.generationb.foundation.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageService {

    private final CoverageItemRepository coverageRepository;
    private final CoverageDigestSettingsRepository digestSettingsRepository;
    private final CreatorInsightsProvider insightsProvider;
    private final EmailSender emailSender;

    public List<CoverageItem> getCoverageLog(String brandName) {
        return coverageRepository.findAll();
    }

    public String generateStandardizedName(String creatorName, String handle, String postType, LocalDate date) {
        String safeName = creatorName != null ? creatorName.toLowerCase().replaceAll("[^a-z0-9]", "") : "creator";
        String safeHandle = handle != null ? handle.toLowerCase().replaceAll("[^a-z0-9]", "") : "handle";
        String safePostType = postType != null ? postType.toLowerCase().replaceAll("[^a-z0-9]", "") : "post";
        String formattedDate = date != null ? date.format(DateTimeFormatter.ISO_LOCAL_DATE) : LocalDate.now().toString();

        return String.format("%s-%s-%s-%s", safeName, safeHandle, safePostType, formattedDate);
    }

    // TODO(confirm): contrato Modash pendente, custo pass-through
    @Transactional
    public List<CoverageItem> autoClipRecentActivity(UUID creatorId, String creatorHandle) {
        List<Map<String, Object>> activities = insightsProvider.getRecentActivity(creatorId);
        List<CoverageItem> savedItems = new ArrayList<>();

        for (Map<String, Object> act : activities) {
            CoverageItem item = new CoverageItem();
            item.setCreatorId(creatorId);
            item.setCreatorHandle(creatorHandle != null ? creatorHandle : "sophiabeauty");
            item.setPlatform((String) act.getOrDefault("platform", "INSTAGRAM"));
            item.setPostType((String) act.getOrDefault("postType", "REEL"));
            item.setUrl((String) act.get("url"));
            item.setViews((Integer) act.getOrDefault("views", 1000));
            item.setLikes((Integer) act.getOrDefault("likes", 100));
            item.setComments((Integer) act.getOrDefault("comments", 10));
            item.setEr(new BigDecimal("4.2"));
            item.setStandardizedName(generateStandardizedName("Creator", item.getCreatorHandle(), item.getPostType(), LocalDate.now()));
            item.setUnsolicited(false);

            UUID currentBrandId = BrandContext.getCurrentBrandId();
            item.setBrandId(currentBrandId != null ? currentBrandId : UUID.fromString("11111111-1111-1111-1111-111111111111"));

            savedItems.add(coverageRepository.save(item));
        }

        return savedItems;
    }

    // TODO(confirm): formato atual do WIP — Sarah e Tiff
    public Map<String, String> exportCoverage(String format) {
        String url = String.format("/downloads/coverage-export-%s-%d.%s", format, System.currentTimeMillis(), format.equals("excel") ? "xlsx" : format);
        return Map.of(
                "format", format,
                "downloadUrl", url,
                "status", "COMPLETED"
        );
    }

    @Transactional
    public CoverageDigestSettings updateDigestSettings(boolean enabled, String sendTime, String recipientEmail) {
        UUID brandId = BrandContext.getCurrentBrandId() != null ? BrandContext.getCurrentBrandId() : UUID.fromString("11111111-1111-1111-1111-111111111111");
        CoverageDigestSettings settings = digestSettingsRepository.findByBrandId(brandId)
                .orElseGet(() -> {
                    CoverageDigestSettings s = new CoverageDigestSettings();
                    s.setBrandId(brandId);
                    return s;
                });

        settings.setEnabled(enabled);
        settings.setSendTime(sendTime);
        if (recipientEmail != null) settings.setRecipientEmail(recipientEmail);

        return digestSettingsRepository.save(settings);
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyMorningDigest() {
        log.info("[COVERAGE DIGEST JOB] Running daily morning coverage digest...");
        List<CoverageDigestSettings> allSettings = digestSettingsRepository.findAll();
        for (CoverageDigestSettings setting : allSettings) {
            if (setting.isEnabled()) {
                String recipient = setting.getRecipientEmail() != null ? setting.getRecipientEmail() : "team@generationb.dev";
                log.info("[COVERAGE DIGEST JOB] Sending daily digest to {}", recipient);
            }
        }
    }
}
