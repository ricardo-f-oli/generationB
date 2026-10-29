package com.generationb.creators.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A creator's audience report for one platform, as CreatorDB returned it.
 *
 * <p>Kept because each one costs 10 credits: opening a profile reads this row, and only an
 * explicit refresh buys a new one. The raw JSON is stored so fields nobody maps today are not
 * lost; {@link CreatorDbLookup#breakdown} reads it.
 */
@Entity
@Table(name = "creator_audience_reports")
@Getter
@Setter
@NoArgsConstructor
public class CreatorAudienceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "source", nullable = false)
    private String source = "CREATORDB";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", columnDefinition = "jsonb", nullable = false)
    private String report;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();
}
