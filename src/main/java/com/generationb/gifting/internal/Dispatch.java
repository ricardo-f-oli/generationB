package com.generationb.gifting.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Q-C2: now extends BaseEntity so a dispatch is brand-scoped and soft-deletable. It previously
 * had no brand_id at all, so the status endpoint could modify any tenant's dispatch by id.
 */
@Entity
@Table(name = "dispatches")
@Getter
@Setter
@NoArgsConstructor
public class Dispatch extends BaseEntity {

    public static final String READY = "READY_TO_DISPATCH";
    public static final String DISPATCHED = "DISPATCHED";
    public static final String DELIVERED = "DELIVERED";
    public static final String RETURNED = "RETURNED";
    public static final String DECLINED = "DECLINED";

    @Column(name = "gifting_run_id")
    private UUID giftingRunId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "sku")
    private String sku;

    @Column(name = "packaging_notes")
    private String packagingNotes;

    @Column(name = "planned_dispatch_date")
    private LocalDate plannedDispatchDate;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "courier")
    private String courier = "Royal Mail";

    @Column(name = "status", nullable = false)
    private String status = READY;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "return_reason")
    private String returnReason;

    /** Requirement #46: reminders are sent relative to the content deadline. */
    @Column(name = "content_deadline")
    private LocalDate contentDeadline;

    @Column(name = "reminder_week_sent_at")
    private Instant reminderWeekSentAt;

    @Column(name = "reminder_48h_sent_at")
    private Instant reminder48hSentAt;

    public static boolean isValidStatus(String value) {
        return READY.equals(value) || DISPATCHED.equals(value) || DELIVERED.equals(value)
                || RETURNED.equals(value) || DECLINED.equals(value);
    }
}
