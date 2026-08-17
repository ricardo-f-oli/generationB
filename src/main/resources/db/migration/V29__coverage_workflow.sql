-- Coverage tracking completion: requirements #11-#15.

ALTER TABLE coverage_items
    ADD COLUMN IF NOT EXISTS caption VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(128);

ALTER TABLE coverage_items DROP CONSTRAINT IF EXISTS chk_coverage_source;
ALTER TABLE coverage_items ADD CONSTRAINT chk_coverage_source
    CHECK (source IN ('MANUAL', 'AUTO_CLIP', 'MENTION', 'IMPORT'));

-- Requirement #11: auto-clipping runs repeatedly, so the same post must not land twice.
-- Partial, because a manually logged item may legitimately have no URL yet.
DELETE FROM coverage_items a
USING coverage_items b
WHERE a.url IS NOT NULL
  AND a.brand_id = b.brand_id
  AND a.url = b.url
  AND a.deleted_at IS NULL AND b.deleted_at IS NULL
  AND (a.created_at, a.id) > (b.created_at, b.id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coverage_brand_url
    ON coverage_items(brand_id, url)
    WHERE url IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_coverage_posted_at ON coverage_items(brand_id, posted_at DESC);

-- Requirement #12: the clipping-name format is a brand setting, not a hard-coded String.format.
-- Placeholders: {creator} {handle} {platform} {type} {date} {brand}
ALTER TABLE coverage_digest_settings
    ADD COLUMN IF NOT EXISTS clipping_name_pattern VARCHAR(255)
        NOT NULL DEFAULT '{brand}_{handle}_{platform}_{type}_{date}',
    ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS include_unsolicited BOOLEAN NOT NULL DEFAULT TRUE;
