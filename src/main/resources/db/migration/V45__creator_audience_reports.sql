-- Requirement #26: the full audience report bought from CreatorDB, one per creator and platform.
--
-- Each report costs 10 credits, so it is kept rather than re-bought every time the profile is
-- opened. The creator row still carries the four summary figures (UK %, top location, age band,
-- gender split); this keeps everything else — every country and every age band.
--
-- `report` is CreatorDB's `data` object as returned, so a later screen can read a field nobody
-- maps today without buying the report again.
CREATE TABLE creator_audience_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'CREATORDB',
    report JSONB NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_creator_audience_report UNIQUE (creator_id, platform),
    CONSTRAINT chk_audience_report_platform CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'YOUTUBE'))
);

COMMENT ON TABLE creator_audience_reports IS
    'Audience demographics bought from CreatorDB, kept so each 10-credit report is bought once.';

COMMENT ON COLUMN creators.insights_source IS
    'Where the measured figures came from: CREATORDB, INSTAGRAM_PUBLIC, YOUTUBE_PUBLIC, '
    'INSTAGRAM_CONNECTED, LEGACY (a former data provider), or NULL if entered by hand.';
