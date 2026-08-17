-- Requirements #49–#55: the reporting module. Previously an empty package.

-- Requirement #50: each brand has report templates matching their existing format.
CREATE TABLE report_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    report_type VARCHAR(40) NOT NULL,
    -- Ordered list of section keys, e.g. ["summary","creator_breakdown","top_posts"].
    sections JSONB NOT NULL DEFAULT '["summary","creator_breakdown"]'::jsonb,
    include_affiliate BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_report_type CHECK (report_type IN ('MONTHLY_SEEDING', 'CAMPAIGN_WRAP', 'MAILER_CONVERSION'))
);
CREATE INDEX idx_report_templates_brand ON report_templates(brand_id);

-- Requirements #51 + #53: a generated report, with the sign-off state machine.
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
    template_id UUID REFERENCES report_templates(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    report_type VARCHAR(40) NOT NULL,
    cadence VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    -- Snapshot of the computed metrics at generation time, so a signed-off report
    -- does not silently change when new coverage lands.
    metrics JSONB,
    submitted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_report_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SENT')),
    CONSTRAINT chk_report_cadence CHECK (cadence IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'CAMPAIGN')),
    CONSTRAINT chk_reports_type CHECK (report_type IN ('MONTHLY_SEEDING', 'CAMPAIGN_WRAP', 'MAILER_CONVERSION'))
);
CREATE INDEX idx_reports_brand ON reports(brand_id);
CREATE INDEX idx_reports_campaign ON reports(campaign_id);
CREATE INDEX idx_reports_status ON reports(status);

-- Requirement #52: chase creators for missing insights.
CREATE TABLE insight_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    chase_count INT NOT NULL DEFAULT 0,
    last_chased_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_insight_request UNIQUE (campaign_id, creator_id),
    CONSTRAINT chk_insight_status CHECK (status IN ('PENDING', 'CHASED', 'RECEIVED', 'WAIVED'))
);
CREATE INDEX idx_insight_requests_campaign ON insight_requests(campaign_id);

-- Requirement #55: the client-set KPIs a shortlisted creator is measured against.
CREATE TABLE campaign_kpi_targets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    min_followers INT,
    max_followers INT,
    min_er NUMERIC(5,2),
    min_uk_audience NUMERIC(5,2),
    target_reach BIGINT,
    preferred_platform VARCHAR(50),
    preferred_niche VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_kpi_per_campaign UNIQUE (campaign_id)
);

-- Requirement #49 needs follower growth, which needs a time series. A single mutable
-- followers_count can never answer "growth".
CREATE TABLE creator_follower_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES creators(id) ON DELETE CASCADE,
    followers_count INT NOT NULL,
    er_percentage NUMERIC(5,2),
    captured_on DATE NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT uq_snapshot_per_day UNIQUE (creator_id, captured_on)
);
CREATE INDEX idx_follower_snapshots_creator ON creator_follower_snapshots(creator_id, captured_on DESC);

-- Seed a baseline snapshot so growth has something to compare against from day one.
INSERT INTO creator_follower_snapshots (creator_id, followers_count, er_percentage, captured_on)
SELECT id, followers_count, er_percentage, CURRENT_DATE - INTERVAL '30 days'
FROM creators WHERE deleted_at IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO creator_follower_snapshots (creator_id, followers_count, er_percentage, captured_on)
SELECT id, followers_count, er_percentage, CURRENT_DATE
FROM creators WHERE deleted_at IS NULL
ON CONFLICT DO NOTHING;

-- Coverage needs to say whether a post is short or long form (#49).
ALTER TABLE coverage_items
    ADD COLUMN IF NOT EXISTS content_form VARCHAR(20),
    ADD COLUMN IF NOT EXISTS impressions BIGINT,
    ADD COLUMN IF NOT EXISTS shares BIGINT,
    ADD COLUMN IF NOT EXISTS saves BIGINT;

UPDATE coverage_items
SET content_form = CASE
    WHEN UPPER(post_type) IN ('REEL', 'STORY', 'TIKTOK', 'SHORT') THEN 'SHORT'
    WHEN UPPER(post_type) IN ('YOUTUBE', 'BLOG', 'VIDEO') THEN 'LONG'
    ELSE 'SHORT'
END
WHERE content_form IS NULL;

-- Default templates so a brand can generate a report immediately.
INSERT INTO report_templates (id, brand_id, name, report_type, sections, is_default) VALUES
    ('4e711111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111',
     'Monthly Seeding Report', 'MONTHLY_SEEDING',
     '["summary","creator_breakdown","top_posts","reconciliation"]'::jsonb, true),
    ('4e722222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     'Campaign Wrap', 'CAMPAIGN_WRAP',
     '["summary","creator_breakdown","top_posts","kpi_vs_target"]'::jsonb, true),
    ('4e733333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
     'Mailer Conversion', 'MAILER_CONVERSION',
     '["summary","reconciliation","creator_breakdown"]'::jsonb, true)
ON CONFLICT (id) DO NOTHING;
