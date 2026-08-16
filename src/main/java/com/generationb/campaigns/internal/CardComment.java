package com.generationb.campaigns.internal;

import com.generationb.foundation.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Requirement #5: comments on a card. The UI showed two hardcoded ones and stored nothing. */
@Entity
@Table(name = "card_comments")
@Getter
@Setter
@NoArgsConstructor
public class CardComment extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;
}
