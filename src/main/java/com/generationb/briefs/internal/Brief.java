package com.generationb.briefs.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.briefs.ToneOfVoice;
import com.generationb.briefs.BriefStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "briefs")
@Getter
@Setter
public class Brief extends BaseEntity {

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @Column(name = "campaign_goal", columnDefinition = "text")
    private String campaignGoal;

    @Column(name = "key_messages", columnDefinition = "text")
    private String keyMessages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deliverables", columnDefinition = "jsonb")
    private List<String> deliverables;

    @Column(name = "budget_min")
    private BigDecimal budgetMin;

    @Column(name = "budget_max")
    private BigDecimal budgetMax;

    @Column(name = "timeline_start")
    private Instant timelineStart;

    @Column(name = "timeline_end")
    private Instant timelineEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone_of_voice")
    private ToneOfVoice toneOfVoice;

    @Column(name = "additional_notes", columnDefinition = "text")
    private String additionalNotes;

    @Column(name = "ai_generated_content", columnDefinition = "text")
    private String aiGeneratedContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BriefStatus status;

    @Column(name = "created_by")
    private UUID createdBy;
}
