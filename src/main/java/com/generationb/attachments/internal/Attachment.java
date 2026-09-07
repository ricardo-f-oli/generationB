package com.generationb.attachments.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
public class Attachment extends BaseEntity {

    public static final String CARD = "CARD";
    public static final String BRIEF = "BRIEF";
    public static final String REPORT = "REPORT";
    public static final String CREATOR = "CREATOR";

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Never leaves the server. See AttachmentResponse. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    public static boolean isValidOwnerType(String value) {
        return CARD.equals(value) || BRIEF.equals(value)
                || REPORT.equals(value) || CREATOR.equals(value);
    }
}
