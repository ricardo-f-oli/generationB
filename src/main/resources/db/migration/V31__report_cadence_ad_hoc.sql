-- Requirement #51 asks for reports at any cadence, and the UI offers an ad-hoc option.
-- V27's check constraint did not allow it, so choosing "ad hoc" was rejected with a 400.

ALTER TABLE reports DROP CONSTRAINT IF EXISTS chk_report_cadence;
ALTER TABLE reports ADD CONSTRAINT chk_report_cadence
    CHECK (cadence IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'CAMPAIGN', 'AD_HOC'));
