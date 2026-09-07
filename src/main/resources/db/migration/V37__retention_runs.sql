-- Requirement #37: evidence that the retention policy is actually applied.
--
-- UK GDPR's accountability principle is not satisfied by having a policy; you have to be able to
-- demonstrate you follow it. A log line is not evidence — it rotates away, and nobody can show a
-- regulator a log line from eighteen months ago. This table is the record: what ran, when, how
-- much it removed, and under which stated rule.
--
-- The rule text is stored per run rather than referenced, on purpose. When the window changes
-- from 90 days to 60, the old rows must keep saying what was actually applied at the time.

CREATE TABLE retention_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Which sweeper. Matches RetentionSweeper.dataset(), e.g. 'Gifting addresses'.
    dataset VARCHAR(120) NOT NULL,

    -- The rule as it stood when this pass ran.
    policy VARCHAR(500) NOT NULL,

    -- Rows removed, or rows that would have been removed on a preview.
    affected INT NOT NULL DEFAULT 0,

    -- A preview from the GDPR screen rather than the nightly job.
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,

    -- Age of the oldest record still held afterwards. A value that stops moving is how you spot
    -- a sweeper that has silently stopped matching anything.
    oldest_remaining TIMESTAMP WITH TIME ZONE,

    -- Null for the scheduled run; set when a person pressed the button.
    triggered_by UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Populated when the sweeper threw. A failed pass has to be visible, not absent.
    error VARCHAR(500),

    ran_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The screen shows the most recent pass per dataset; the regulator question is "show me this
-- year", which is the same index read the other way.
CREATE INDEX idx_retention_runs_dataset ON retention_runs(dataset, ran_at DESC);
CREATE INDEX idx_retention_runs_ran_at ON retention_runs(ran_at DESC);

-- Retention on the retention log itself. Six years matches the consent records it sits beside:
-- the evidence has to outlive the data it justifies removing.
COMMENT ON TABLE retention_runs IS
    'Requirement #37 accountability evidence. Kept 6 years, longer than any dataset it reports '
    'on, so the agency can always show what was applied and when.';
