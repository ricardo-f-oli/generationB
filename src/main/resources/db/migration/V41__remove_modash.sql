-- Modash is gone. The contract is over and the integration has been deleted, so its traces in the
-- schema go with it.
--
--  * quality_band came only from Modash's audience-credibility score. Nothing free can measure
--    that, and a band nobody can refresh would sit on reports as if it were current.
--  * insights_external_id was Modash's own account id. It means nothing to any other source.
--  * insights_source = 'MODASH' rows keep their figures (followers, UK %, age, gender) — they are
--    still the agency's best information on those creators — but are relabelled LEGACY so the
--    screen says they came from a former provider and may be out of date. The next free refresh
--    from Instagram or YouTube overwrites the label with the source that answered.

UPDATE creators SET insights_source = 'LEGACY' WHERE insights_source = 'MODASH';

ALTER TABLE creators DROP COLUMN IF EXISTS quality_band;
ALTER TABLE creators DROP COLUMN IF EXISTS insights_external_id;

COMMENT ON COLUMN creators.insights_source IS
    'Where the measured figures came from: INSTAGRAM_PUBLIC, YOUTUBE_PUBLIC, INSTAGRAM_CONNECTED, '
    'LEGACY (a former data provider), or NULL if entered by hand.';
COMMENT ON COLUMN creators.insights_refreshed_at IS
    'When a platform last answered for this creator. Drives the staleness check for bulk refreshes.';
