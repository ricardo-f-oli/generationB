-- The creator-data vendor (Modash) is connected, which unblocks requirements #10, #11, #25
-- and #26. Two changes are needed to hold what it returns honestly.
--
-- 1. Provenance on the creator. The demographic columns added in V22 have sat empty because
--    nothing populated them. Now that something does, a reader has to be able to tell a figure
--    pulled from the vendor last Tuesday from one somebody typed in a year ago, and the
--    enrichment job has to know what it can skip.
--
-- 2. Views become nullable. Instagram publishes no view count on any post type — verified
--    against the live API on both a brand account and a creator account, carousels and reels
--    alike. Storing 0 for every auto-clipped Instagram post would put "0 views" in front of a
--    client, which reads as "nobody saw it" rather than "Instagram does not tell us". This is
--    the same decision already taken for impressions in V17, applied to the column that the
--    vendor turned out not to fill. TikTok does supply play counts, so TikTok rows keep theirs.

ALTER TABLE creators
    ADD COLUMN IF NOT EXISTS insights_source VARCHAR(20),
    ADD COLUMN IF NOT EXISTS insights_external_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS insights_refreshed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN creators.insights_source IS
    'Where the audience figures came from: MODASH, or NULL if entered by hand.';
COMMENT ON COLUMN creators.insights_refreshed_at IS
    'When the vendor last answered for this creator. Drives the staleness check so a bulk '
    'refresh does not re-buy a report that is still current.';

-- Finding the creators worth refreshing: oldest first, never-enriched first.
CREATE INDEX IF NOT EXISTS idx_creators_insights_refreshed
    ON creators(insights_refreshed_at NULLS FIRST)
    WHERE deleted_at IS NULL;

-- --------------------------------------------------------------------------
-- Views: "not tracked" is a real answer, and 0 is not a synonym for it.
-- --------------------------------------------------------------------------

ALTER TABLE coverage_items
    ALTER COLUMN views DROP NOT NULL,
    ALTER COLUMN views DROP DEFAULT;

COMMENT ON COLUMN coverage_items.views IS
    'NULL means the platform does not publish a view count for this post — Instagram never '
    'does. It does not mean zero, and reports must print "Not tracked" rather than 0.';

-- Rows already logged against the mock provider carry a fabricated 0 rather than a measured
-- one. Only the auto-clipped and mention rows are touched: a 0 someone typed into the manual
-- form is a deliberate statement and stays.
UPDATE coverage_items
   SET views = NULL
 WHERE views = 0
   AND source IN ('AUTO_CLIP', 'MENTION');
