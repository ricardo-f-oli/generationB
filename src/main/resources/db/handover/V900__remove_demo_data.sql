-- Remove the sample dataset for client handover.  *** OPT-IN. NOT IN THE DEFAULT PATH. ***
--
-- This file lives in `db/handover`, which is NOT in `spring.flyway.locations` by default. It
-- runs only where FLYWAY_LOCATIONS names it:
--
--   FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/handover
--
-- That separation is the whole point, and it is not fussiness:
--
--   * In production this script is a data-loss event. `DELETE FROM creators` does not know the
--     difference between a seeded Priya Patel and a creator the agency signed last week. It must
--     never run anywhere by default.
--   * Locally and in tests the sample data is load-bearing. TenantIsolationTest addresses a
--     campaign card seeded by V33 by its id; RetentionIntegrationTest needs rows old enough to
--     sweep. Running this as an ordinary migration would fail the suite.
--
-- So it is deliberately awkward to run: someone has to decide an environment should be empty,
-- and say so. Staging, for a client handover, is that environment.
--
-- Demo rows were seeded across five migrations (V19, V20, V22, V24, V33) rather than one, and
-- those have already run everywhere, so they cannot simply be edited — Flyway validates their
-- checksums. Removal has to be additive, which is what this is.
--
-- WHAT GOES: every transactional record. Creators, campaigns, boards, briefs, coverage,
-- gifting, outreach, reports. The client's first creator should be a real one.
--
-- WHAT STAYS, deliberately:
--   contract_clauses             the clause library — briefs cannot be assembled without it
--   board_templates / _columns   the 7-stage kanban template a new campaign is built from
--   report_templates             the monthly seeding report layout
--   content_style_tags           the style taxonomy creators are tagged against
--   brands                       B. The Agency, plus Mediheal and Katie Loxton (real clients)
--   users                        handled by V44 — kept, but on a single-use password
--
-- Deleting those would not be "clean", it would be broken: the product has no UI for authoring
-- a board template, so an empty board_templates means no campaign can ever be created.
--
-- ORDER IS LOAD-BEARING. Some foreign keys cascade and some restrict (gifting_runs, kanban_boards,
-- campaign_cards and coverage_items all reference campaigns with no ON DELETE clause), so this
-- runs children-first in dependency order. It is not alphabetical and should not be tidied into
-- alphabetical.

-- ---------------------------------------------------------- creator-adjacent leaves ---
DELETE FROM waitlist_entries;
DELETE FROM shortlist_items;
DELETE FROM shortlists;
DELETE FROM saved_views;
DELETE FROM insight_requests;
DELETE FROM global_suppression_list;
DELETE FROM instagram_hashtag_lookups;

-- ------------------------------------------------------------------------- outreach ---
DELETE FROM follow_up_suggestions;
DELETE FROM email_threads;
DELETE FROM outreach_recipients;
DELETE FROM outreach_campaigns;

-- -------------------------------------------------------------------------- gifting ---
DELETE FROM gifting_addresses;
DELETE FROM dispatches;
DELETE FROM brand_orders;
DELETE FROM gifting_runs;

-- ------------------------------------------------------------- creator child records ---
DELETE FROM creator_style_tags;
DELETE FROM creator_send_history;
DELETE FROM creator_platform_connections;
DELETE FROM creator_connection_requests;
DELETE FROM creator_note_revisions;
DELETE FROM creator_notes;
DELETE FROM creator_follower_snapshots;
DELETE FROM creator_custom_attributes;
DELETE FROM creator_brand_links;
DELETE FROM consent_records;

-- ------------------------------------------------------- campaign and coverage records ---
DELETE FROM coverage_items;
DELETE FROM coverage_digest_settings;   -- absent means defaults; CoverageService handles it
DELETE FROM card_comments;
DELETE FROM campaign_kpi_targets;
DELETE FROM campaign_cards;
DELETE FROM kanban_columns;
DELETE FROM kanban_boards;
DELETE FROM reports;
DELETE FROM brief_shares;
DELETE FROM brief_clauses;
DELETE FROM briefs;

-- Creators and campaigns last: everything above pointed at them.
DELETE FROM creators;
DELETE FROM campaigns;

-- ---------------------------------------------------------------- attachments and logs ---
-- Attachment rows reference objects in a bucket that the client's environment does not have,
-- so a kept row would render as a broken download.
DELETE FROM attachments;

-- Development noise, not evidence: every audited action in these tables was performed by a
-- developer against sample data. Starting the client's audit trail at their first real action
-- is both cleaner and more defensible than handing them a log of someone else's testing.
DELETE FROM audit_log;
DELETE FROM retention_runs;
DELETE FROM login_attempts;

-- Any session or reset link issued during development dies here.
DELETE FROM password_reset_tokens;
DELETE FROM refresh_tokens;
