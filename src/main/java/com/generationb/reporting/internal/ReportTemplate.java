package com.generationb.reporting.internal;

import com.generationb.foundation.BaseEntity;
import com.generationb.reporting.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** Requirement #50: each brand has templates matching their existing report format. */
@Entity
@Table(name = "report_templates")
@Getter
@Setter
@NoArgsConstructor
public class ReportTemplate extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", columnDefinition = "jsonb", nullable = false)
    private List<String> sections;

    @Column(name = "include_affiliate", nullable = false)
    private boolean includeAffiliate = false;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;
}
