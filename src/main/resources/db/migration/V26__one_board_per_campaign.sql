-- A campaign has exactly one board. Nothing enforced this, so repeated calls to
-- POST /api/campaigns/{id}/boards silently created duplicates, and the lookup by campaign
-- then failed with "Query did not return a unique result".

-- 1. Keep the oldest board per campaign; soft-delete the rest.
UPDATE kanban_boards b
SET deleted_at = CURRENT_TIMESTAMP
WHERE b.deleted_at IS NULL
  AND EXISTS (
      SELECT 1 FROM kanban_boards older
      WHERE older.campaign_id = b.campaign_id
        AND older.deleted_at IS NULL
        AND (older.created_at < b.created_at
             OR (older.created_at = b.created_at AND older.id < b.id))
  );

-- 2. Re-home cards from a dead board onto the surviving board's stage of the same name.
--    (Moving the *columns* across would leave the keeper with two of every stage.)
UPDATE campaign_cards cc
SET board_id = keep.id,
    column_id = COALESCE(keep_col.id, cc.column_id)
FROM kanban_boards dead
JOIN kanban_boards keep
  ON keep.campaign_id = dead.campaign_id
 AND keep.deleted_at IS NULL
LEFT JOIN kanban_columns dead_col ON dead_col.board_id = dead.id
LEFT JOIN kanban_columns keep_col
  ON keep_col.board_id = keep.id
 AND LOWER(keep_col.name) = LOWER(dead_col.name)
 AND keep_col.deleted_at IS NULL
WHERE cc.board_id = dead.id
  AND dead.deleted_at IS NOT NULL
  AND cc.column_id = dead_col.id;

-- 3. Retire the dead boards' stages.
UPDATE kanban_columns c
SET deleted_at = CURRENT_TIMESTAMP
WHERE c.deleted_at IS NULL
  AND EXISTS (
      SELECT 1 FROM kanban_boards dead
      WHERE dead.id = c.board_id AND dead.deleted_at IS NOT NULL
  );

-- 4. De-duplicate stages within a board by name, keeping the lowest display_order.
UPDATE campaign_cards cc
SET column_id = keeper.id
FROM kanban_columns dup
JOIN kanban_columns keeper
  ON keeper.board_id = dup.board_id
 AND LOWER(keeper.name) = LOWER(dup.name)
 AND keeper.deleted_at IS NULL
 AND (keeper.display_order < dup.display_order
      OR (keeper.display_order = dup.display_order AND keeper.id < dup.id))
WHERE cc.column_id = dup.id
  AND dup.deleted_at IS NULL;

UPDATE kanban_columns dup
SET deleted_at = CURRENT_TIMESTAMP
WHERE dup.deleted_at IS NULL
  AND EXISTS (
      SELECT 1 FROM kanban_columns keeper
      WHERE keeper.board_id = dup.board_id
        AND LOWER(keeper.name) = LOWER(dup.name)
        AND keeper.deleted_at IS NULL
        AND (keeper.display_order < dup.display_order
             OR (keeper.display_order = dup.display_order AND keeper.id < dup.id))
  );

-- 5. Enforce it from here on.
CREATE UNIQUE INDEX uq_board_per_campaign
    ON kanban_boards (campaign_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_column_name_per_board
    ON kanban_columns (board_id, LOWER(name))
    WHERE deleted_at IS NULL;
