package com.generationb.campaigns.internal;

import com.generationb.campaigns.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignCardRepository extends JpaRepository<CampaignCard, UUID> {

    @Query("""
        SELECT c FROM CampaignCard c
        WHERE c.boardId = :boardId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        ORDER BY c.position ASC, c.createdAt ASC
        """)
    List<CampaignCard> findAllByBoardIdAndBrandId(@Param("boardId") UUID boardId);

    /** Q-G3: cards for one column, paginated. */
    @Query("""
        SELECT c FROM CampaignCard c
        WHERE c.columnId = :columnId AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        ORDER BY c.position ASC
        """)
    Page<CampaignCard> findByColumn(@Param("columnId") UUID columnId, Pageable pageable);

    @Query("""
        SELECT c FROM CampaignCard c
        WHERE c.id = :id AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    Optional<CampaignCard> findByIdAndBrandId(@Param("id") UUID id);

    @Query("""
        SELECT c FROM CampaignCard c
        WHERE c.id IN :ids AND c.brandId = ?#{@brandContext.brandId} AND c.deletedAt IS NULL
        """)
    List<CampaignCard> findAllScopedByIds(@Param("ids") List<UUID> ids);

    @Query("""
        SELECT COALESCE(MAX(c.position), -1) FROM CampaignCard c
        WHERE c.columnId = :columnId AND c.deletedAt IS NULL
        """)
    int findMaxPosition(@Param("columnId") UUID columnId);

    @Query("""
        SELECT COUNT(c) > 0 FROM CampaignCard c
        WHERE c.boardId = :boardId AND c.creatorId = :creatorId AND c.deletedAt IS NULL
        """)
    boolean existsOnBoard(@Param("boardId") UUID boardId, @Param("creatorId") UUID creatorId);

    /**
     * Requirement #9: the filter behind the saved views. All parameters optional.
     */
    @Query("""
        SELECT c FROM CampaignCard c
        WHERE c.boardId = :boardId
          AND c.brandId = ?#{@brandContext.brandId}
          AND c.deletedAt IS NULL
          AND (:assigneeId IS NULL OR c.assigneeId = :assigneeId)
          AND (:blocked IS NULL OR c.blocked = :blocked)
          AND (:approvalStatus IS NULL OR c.approvalStatus = :approvalStatus)
          AND (CAST(:dueBefore AS date) IS NULL OR c.deadline <= :dueBefore)
        ORDER BY c.position ASC
        """)
    List<CampaignCard> findFiltered(@Param("boardId") UUID boardId,
                                    @Param("assigneeId") UUID assigneeId,
                                    @Param("blocked") Boolean blocked,
                                    @Param("approvalStatus") ApprovalStatus approvalStatus,
                                    @Param("dueBefore") LocalDate dueBefore);
}
