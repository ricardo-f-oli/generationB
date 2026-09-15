-- Reach per post, so a campaign report can say how many people a creator's post reached.
--
-- Without the creator connecting their account, no platform publishes true reach (unique viewers)
-- to a third party. So this stores the best figure we genuinely have, per post:
--
--   * views, where the platform publishes them (YouTube, TikTok);
--   * otherwise the creator's follower count when the post was captured — the audience the post
--     was published to, which is how Instagram reach is estimated without insights access.
--
-- NULL means neither was known. Reports label the total "Estimated reach" for that reason.

ALTER TABLE coverage_items ADD COLUMN IF NOT EXISTS reach BIGINT;

-- Existing rows: views where known, else the creator's current follower count as the closest
-- available approximation of their audience at the time.
UPDATE coverage_items ci
SET reach = COALESCE(ci.views, NULLIF(c.followers_count, 0))
FROM creators c
WHERE c.id = ci.creator_id AND ci.reach IS NULL;

UPDATE coverage_items SET reach = views WHERE reach IS NULL AND views IS NOT NULL;

COMMENT ON COLUMN coverage_items.reach IS
    'Estimated reach: views where the platform publishes them, else the creator''s follower count '
    'at capture. NULL when neither was known.';
