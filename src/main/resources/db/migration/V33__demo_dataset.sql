-- =============================================================================
-- Demo dataset
-- =============================================================================
-- A dataset you can actually exercise the product against: real campaigns and
-- boards, outreach old enough to trigger the follow-up scanner, dispatches whose
-- deadlines fall exactly where the reminder job looks, and reports in every
-- lifecycle state.
--
-- Two deliberate choices:
--
-- 1. EVERY DATE IS RELATIVE TO CURRENT_DATE. Seeding absolute dates means the
--    time-sensitive paths (follow-ups after 7 days, gift reminders at 7 days and
--    48 hours) silently stop firing a week after the seed was written. These stay
--    correct whenever the migration runs.
--
-- 2. IT INCLUDES THE AWKWARD CASES, not just the happy path — a creator with no
--    email, one who opted out, one already excluded from gifting, one anonymised
--    under GDPR, a post with zero views, and a viral post large enough to prove
--    the columns are BIGINT. Those are the rows that break things.
--
-- Everything is ON CONFLICT DO NOTHING so re-running is harmless.
-- =============================================================================

-- ---------------------------------------------------------------- creators ---
-- Six additions, each chosen to break something if it is handled carelessly.

INSERT INTO creators (id, name, handle, email, primary_platform, followers_count,
                      er_percentage, location, niche, opt_in_status, opt_in_step,
                      follower_band, bio, uk_audience_pct, quality_band,
                      gifting_excluded, gifting_exclusion_reason, anonymised_at,
                      created_at, updated_at)
VALUES
  -- No email. Cannot be emailed, chased or sent an address form — every send
  -- path has to skip her and say why rather than throwing.
  ('c9999999-9999-9999-9999-999999999999', 'Priya Patel', 'priyastyle', NULL,
   'INSTAGRAM', 28000, 5.40, 'Leicester, UK', 'Fashion', 'APPROVED', 2,
   '10K-50K', 'Modest fashion and styling. Contact via DM only.', 78.0, 'Strong',
   false, NULL, NULL, now() - interval '120 days', now()),

  -- Opted out. Suppression must win over every other rule, on every brand.
  ('ca000000-0000-0000-0000-00000000000a', 'Tom Gardner', 'tomgrows', 'tom@example.com',
   'YOUTUBE', 96000, 3.10, 'Bristol, UK', 'Gardening', 'APPROVED', 2,
   '50K-100K', 'Allotment diaries and slow gardening.', 91.0, 'Average',
   false, NULL, NULL, now() - interval '200 days', now()),

  -- Already excluded from gifting after refusing a parcel previously.
  ('cb000000-0000-0000-0000-00000000000b', 'Zara Khan', 'zarakhanfit', 'zara@example.com',
   'INSTAGRAM', 143000, 4.60, 'Birmingham, UK', 'Fitness', 'APPROVED', 2,
   '100K-250K', 'Strength training for beginners.', 84.0, 'Strong',
   true, 'Asked not to receive unsolicited product', NULL,
   now() - interval '300 days', now()),

  -- Anonymised under right-to-erasure. Must never appear in a client report or
  -- an export, and must not be re-importable.
  ('cc000000-0000-0000-0000-00000000000c', 'Deleted Creator', 'deleted-cc000000', NULL,
   'INSTAGRAM', 0, 0.00, NULL, NULL, 'OPTED_OUT', 0,
   NULL, NULL, NULL, NULL, false, NULL, now() - interval '30 days',
   now() - interval '400 days', now()),

  -- Nano creator: tiny audience, exceptional engagement. Breaks any code that
  -- assumes reach scales with followers, and is the one that passes an ER gate
  -- while failing a follower gate.
  ('cd000000-0000-0000-0000-00000000000d', 'Isla Reads', 'islareads', 'isla@example.com',
   'TIKTOK', 4200, 11.80, 'Glasgow, UK', 'Books', 'APPROVED', 2,
   'Under 10K', 'Cosy book reviews. Small but very chatty corner of BookTok.',
   88.0, 'Strong', false, NULL, NULL, now() - interval '45 days', now()),

  -- Mega creator: 1.2M followers, low engagement, mostly non-UK. Fails a UK
  -- audience gate and an ER gate while passing every follower gate.
  ('ce000000-0000-0000-0000-00000000000e', 'Jay Global', 'jayglobal', 'jay@example.com',
   'YOUTUBE', 1240000, 1.30, 'London, UK', 'Tech', 'APPROVED', 2,
   '250K+', 'Consumer tech reviews with a largely international audience.',
   22.0, 'Average', false, NULL, NULL, now() - interval '500 days', now())
ON CONFLICT (id) DO NOTHING;

-- Tom is on the global suppression list, which is what actually blocks sends.
INSERT INTO global_suppression_list (id, creator_id, email, handle, reason, opted_out_at, source, brand_id)
VALUES ('d0000000-0000-0000-0000-000000000001', 'ca000000-0000-0000-0000-00000000000a',
        'tom@example.com', 'tomgrows', 'Unsubscribed from a seeding email',
        now() - interval '60 days', 'UNSUBSCRIBE_LINK', NULL)
ON CONFLICT DO NOTHING;

-- Brand relationships, so the cross-brand duplicate warning has something to say.
INSERT INTO creator_brand_links (id, creator_id, brand_id, relationship_status,
                                 first_engaged_at, last_engaged_at, created_at, updated_at)
VALUES
  ('d1000000-0000-0000-0000-000000000001', 'cd000000-0000-0000-0000-00000000000d',
   '11111111-1111-1111-1111-111111111111', 'CONTACTED',
   now() - interval '20 days', now() - interval '12 days', now(), now()),
  ('d1000000-0000-0000-0000-000000000002', 'ce000000-0000-0000-0000-00000000000e',
   '11111111-1111-1111-1111-111111111111', 'PROSPECT',
   now() - interval '10 days', now() - interval '10 days', now(), now()),
  -- Zara has worked with Mediheal — so adding her to a B. The Agency list should
  -- raise the "already worked with another brand" flag.
  ('d1000000-0000-0000-0000-000000000003', 'cb000000-0000-0000-0000-00000000000b',
   '11111111-2222-2222-2222-111111111111', 'WORKED_WITH',
   now() - interval '90 days', now() - interval '30 days', now(), now())
ON CONFLICT DO NOTHING;

-- Follower snapshots. Most creators get two points a month apart so growth is
-- measurable; Isla deliberately gets only one, so the report has to say growth
-- cannot be calculated for her rather than reporting zero.
INSERT INTO creator_follower_snapshots (id, creator_id, followers_count, er_percentage, captured_on)
VALUES
  ('d2000000-0000-0000-0000-000000000001', 'c9999999-9999-9999-9999-999999999999', 26100, 5.10, CURRENT_DATE - 30),
  ('d2000000-0000-0000-0000-000000000002', 'c9999999-9999-9999-9999-999999999999', 28000, 5.40, CURRENT_DATE),
  ('d2000000-0000-0000-0000-000000000003', 'ca000000-0000-0000-0000-00000000000a', 94500, 3.00, CURRENT_DATE - 30),
  ('d2000000-0000-0000-0000-000000000004', 'ca000000-0000-0000-0000-00000000000a', 96000, 3.10, CURRENT_DATE),
  ('d2000000-0000-0000-0000-000000000005', 'cb000000-0000-0000-0000-00000000000b', 139000, 4.40, CURRENT_DATE - 30),
  ('d2000000-0000-0000-0000-000000000006', 'cb000000-0000-0000-0000-00000000000b', 143000, 4.60, CURRENT_DATE),
  -- A creator who lost followers over the month. Growth must be allowed to be negative.
  ('d2000000-0000-0000-0000-000000000007', 'ce000000-0000-0000-0000-00000000000e', 1268000, 1.40, CURRENT_DATE - 30),
  ('d2000000-0000-0000-0000-000000000008', 'ce000000-0000-0000-0000-00000000000e', 1240000, 1.30, CURRENT_DATE),
  -- Isla: one snapshot only.
  ('d2000000-0000-0000-0000-000000000009', 'cd000000-0000-0000-0000-00000000000d', 4200, 11.80, CURRENT_DATE)
ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------- campaigns ---

INSERT INTO campaigns (id, brand_id, name, campaign_type, status, start_date, end_date,
                       created_by, created_at, updated_at)
VALUES
  ('e1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask — August Seeding', 'SEEDING', 'ACTIVE',
   now() - interval '25 days', now() + interval '20 days',
   '22222222-2222-2222-2222-222222222222', now() - interval '25 days', now()),

  ('e1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'Katie Loxton — Autumn Handbags', 'GIFTING', 'ACTIVE',
   now() - interval '10 days', now() + interval '35 days',
   '44444444-4444-4444-4444-444444444444', now() - interval '10 days', now()),

  -- A finished campaign, so a campaign-wrap report has something real behind it.
  ('e1000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
   'Spring Skincare Push', 'PAID', 'COMPLETED',
   now() - interval '120 days', now() - interval '60 days',
   '22222222-2222-2222-2222-222222222222', now() - interval '120 days', now()),

  -- An empty campaign. Every screen should cope with a campaign that has no
  -- coverage, no cards and no sends without showing zeros as if they were results.
  ('e1000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
   'Winter Concept (not started)', 'SEEDING', 'PAUSED',
   NULL, NULL, '22222222-2222-2222-2222-222222222222', now() - interval '2 days', now())
ON CONFLICT (id) DO NOTHING;

-- Boards and stages for the two live campaigns.
INSERT INTO kanban_boards (id, campaign_id, brand_id, name, created_at, updated_at)
VALUES
  ('e2000000-0000-0000-0000-000000000001', 'e1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'August Seeding board', now(), now()),
  ('e2000000-0000-0000-0000-000000000002', 'e1000000-0000-0000-0000-000000000002',
   '11111111-1111-1111-1111-111111111111', 'Autumn Handbags board', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO kanban_columns (id, board_id, brand_id, name, display_order,
                            requires_director_approval, requires_client_approval,
                            triggers_email, created_at, updated_at)
VALUES
  ('e3000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Target list',        1, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Contacted',          2, false, false, true,  now(), now()),
  ('e3000000-0000-0000-0000-000000000003', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Confirmed',          3, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000004', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Product sent',       4, false, false, false, now(), now()),
  -- The approval gate: moving a card here is refused without director sign-off.
  ('e3000000-0000-0000-0000-000000000005', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Content approved',   5, true,  true,  false, now(), now()),
  ('e3000000-0000-0000-0000-000000000006', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Live',               6, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000007', 'e2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Reported',           7, false, false, false, now(), now()),

  ('e3000000-0000-0000-0000-000000000011', 'e2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Target list',        1, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000012', 'e2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Address requested',  2, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000013', 'e2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Dispatched',         3, false, false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000014', 'e2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Content approved',   4, true,  false, false, now(), now()),
  ('e3000000-0000-0000-0000-000000000015', 'e2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Live',               5, false, false, false, now(), now())
ON CONFLICT DO NOTHING;

-- Cards spread across the stages, including a blocked one and one already paid.
INSERT INTO campaign_cards (id, board_id, column_id, brand_id, creator_id, campaign_id,
                            deliverables, fee_amount, fee_currency, deadline,
                            payment_status, approval_status, notes, position,
                            assignee_id, blocked, approved_by, approved_at,
                            created_at, updated_at)
VALUES
  ('e4000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001', 'e3000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'cd000000-0000-0000-0000-00000000000d', 'e1000000-0000-0000-0000-000000000001',
   '["1x TikTok"]'::jsonb, NULL, 'GBP', CURRENT_DATE + 21, 'UNPAID', 'PENDING',
   'Nano creator — very high ER, worth testing.', 1,
   '44444444-4444-4444-4444-444444444444', false, NULL, NULL, now(), now()),

  ('e4000000-0000-0000-0000-000000000002', 'e2000000-0000-0000-0000-000000000001', 'e3000000-0000-0000-0000-000000000002',
   '11111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001',
   '["1x Reel", "3x Stories"]'::jsonb, 450.00, 'GBP', CURRENT_DATE + 14, 'UNPAID', 'PENDING',
   NULL, 1, '44444444-4444-4444-4444-444444444444', false, NULL, NULL, now(), now()),

  -- Blocked: waiting on the creator's address.
  ('e4000000-0000-0000-0000-000000000003', 'e2000000-0000-0000-0000-000000000001', 'e3000000-0000-0000-0000-000000000003',
   '11111111-1111-1111-1111-111111111111', 'c9999999-9999-9999-9999-999999999999', 'e1000000-0000-0000-0000-000000000001',
   '["1x Reel"]'::jsonb, 300.00, 'GBP', CURRENT_DATE + 10, 'UNPAID', 'PENDING',
   'No email on file — reach out via DM for the address.', 1,
   '55555555-5555-5555-5555-555555555555', true, NULL, NULL, now(), now()),

  ('e4000000-0000-0000-0000-000000000004', 'e2000000-0000-0000-0000-000000000001', 'e3000000-0000-0000-0000-000000000004',
   '11111111-1111-1111-1111-111111111111', 'c3333333-3333-3333-3333-333333333333', 'e1000000-0000-0000-0000-000000000001',
   '["1x TikTok", "2x Stories"]'::jsonb, 600.00, 'GBP', CURRENT_DATE + 7, 'TO_PAY', 'PENDING',
   NULL, 1, '44444444-4444-4444-4444-444444444444', false, NULL, NULL, now(), now()),

  -- Already through the gate: approved by the director, and paid.
  ('e4000000-0000-0000-0000-000000000005', 'e2000000-0000-0000-0000-000000000001', 'e3000000-0000-0000-0000-000000000006',
   '11111111-1111-1111-1111-111111111111', 'c5555555-5555-5555-5555-555555555555', 'e1000000-0000-0000-0000-000000000001',
   '["1x Reel"]'::jsonb, 500.00, 'GBP', CURRENT_DATE - 3, 'PAID', 'APPROVED',
   'Went live early, strong engagement.', 1,
   '44444444-4444-4444-4444-444444444444', false,
   '33333333-3333-3333-3333-333333333333', now() - interval '4 days', now(), now()),

  ('e4000000-0000-0000-0000-000000000006', 'e2000000-0000-0000-0000-000000000002', 'e3000000-0000-0000-0000-000000000011',
   '11111111-1111-1111-1111-111111111111', 'c7777777-7777-7777-7777-777777777777', 'e1000000-0000-0000-0000-000000000002',
   '["1x TikTok"]'::jsonb, NULL, 'GBP', NULL, 'UNPAID', 'PENDING', NULL, 1,
   NULL, false, NULL, NULL, now(), now())
ON CONFLICT DO NOTHING;

-- KPI targets, so the match indicator has something to score against. Chosen so
-- the seeded creators land across all three bands rather than all passing.
INSERT INTO campaign_kpi_targets (id, brand_id, campaign_id, min_followers, max_followers,
                                  min_er, min_uk_audience, target_reach,
                                  preferred_platform, preferred_niche, created_at, updated_at)
VALUES
  ('e5000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 20000, 250000, 4.00, 70.0, 500000,
   'INSTAGRAM', 'Beauty', now(), now()),
  ('e5000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000002', 50000, NULL, 3.50, NULL, 1000000,
   'TIKTOK', 'Fashion', now(), now())
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- outreach ---
-- Sent 12 days ago against a 7-day no-reply window, so the follow-up scanner has
-- genuinely eligible recipients the moment you open the screen.

INSERT INTO outreach_campaigns (id, brand_id, campaign_id, subject, body, product_name,
                                outreach_type, sent_at, status, created_by,
                                no_reply_window_days, created_at, updated_at)
VALUES
  ('f1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001',
   'Collaboration with {brand}',
   E'Hi {first_name},\n\nWe are sending the new Mediheal hydrating mask to a small group of UK skincare creators and would love to include {handle}.\n\nIf you are interested, reply here and I will send the details across.\n\nBest wishes',
   'Mediheal Hydrating Mask', 'INITIAL_OUTREACH',
   now() - interval '12 days', 'SENT', '44444444-4444-4444-4444-444444444444',
   7, now() - interval '13 days', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO outreach_recipients (id, outreach_campaign_id, brand_id, creator_id,
                                 creator_email, creator_first_name, creator_handle,
                                 resolved_subject, status, sent_at, opened_at, replied_at,
                                 failure_reason, bounced_at, unsubscribed_at,
                                 created_at, updated_at)
VALUES
  -- Eligible for a follow-up: sent, never opened, no reply, window closed.
  ('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222',
   'marcus@example.com', 'Marcus', 'marcuslifts', 'Collaboration with B. The Agency',
   'SENT', now() - interval '12 days', NULL, NULL, NULL, NULL, NULL, now() - interval '12 days', now()),

  -- Eligible: opened it, still never replied. The most worth chasing.
  ('f2000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c6666666-6666-6666-6666-666666666666',
   'david@example.com', 'David', 'davidtech', 'Collaboration with B. The Agency',
   'SENT', now() - interval '12 days', now() - interval '11 days', NULL, NULL, NULL, NULL, now() - interval '12 days', now()),

  -- Eligible, and the awkward one: no email address, so the chaser has to skip
  -- her without failing the whole batch.
  ('f2000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c9999999-9999-9999-9999-999999999999',
   NULL, 'Priya', 'priyastyle', 'Collaboration with B. The Agency',
   'SENT', now() - interval '12 days', NULL, NULL, NULL, NULL, NULL, now() - interval '12 days', now()),

  -- NOT eligible: she replied. Must never be chased.
  ('f2000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111',
   'sophia@example.com', 'Sophia', 'sophiabeauty', 'Collaboration with B. The Agency',
   'REPLIED', now() - interval '12 days', now() - interval '11 days', now() - interval '10 days',
   NULL, NULL, NULL, now() - interval '12 days', now()),

  -- NOT eligible: sent only 2 days ago, window still open.
  ('f2000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c5555555-5555-5555-5555-555555555555',
   'chloe@example.com', 'Chloe', 'chloeglow', 'Collaboration with B. The Agency',
   'SENT', now() - interval '2 days', NULL, NULL, NULL, NULL, NULL, now() - interval '2 days', now()),

  -- Hard bounce, and a soft failure. Neither should be chased.
  ('f2000000-0000-0000-0000-000000000006', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c4444444-4444-4444-4444-444444444444',
   'liam@example.com', 'Liam', 'liamtravels', 'Collaboration with B. The Agency',
   'BOUNCED', now() - interval '12 days', NULL, NULL,
   'Recipient address does not exist', now() - interval '12 days', NULL, now() - interval '12 days', now()),

  ('f2000000-0000-0000-0000-000000000007', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'c7777777-7777-7777-7777-777777777777',
   'emma@example.com', 'Emma', 'emmacooks', 'Collaboration with B. The Agency',
   'FAILED', NULL, NULL, NULL, 'Provider rejected the message', NULL, NULL, now() - interval '12 days', now()),

  -- Unsubscribed from this very send. Suppression must hold from here on.
  ('f2000000-0000-0000-0000-000000000008', 'f1000000-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', 'ca000000-0000-0000-0000-00000000000a',
   'tom@example.com', 'Tom', 'tomgrows', 'Collaboration with B. The Agency',
   'UNSUBSCRIBED', now() - interval '12 days', now() - interval '12 days', NULL,
   NULL, NULL, now() - interval '11 days', now() - interval '12 days', now())
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------- gifting ---

INSERT INTO gifting_runs (id, brand_id, campaign_id, name, product_name, mailer_text,
                          comp_slip_status, approved_by, approved_at, created_at, updated_at)
VALUES
  ('a1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'August seeding — wave 1',
   'Mediheal Hydrating Mask Box',
   E'Thank you for being part of our August seeding.\n\nWe hope you enjoy the mask — we would love to see how you get on.',
   'APPROVED', '33333333-3333-3333-3333-333333333333', now() - interval '20 days',
   now() - interval '22 days', now()),

  -- Awaiting sign-off: dispatching from this run must be refused.
  ('a1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000002', 'Autumn handbags — wave 1',
   'Katie Loxton Autumn Tote', NULL,
   'PENDING', NULL, NULL, now() - interval '5 days', now())
ON CONFLICT (id) DO NOTHING;

-- Addresses: captured, asked-but-not-answered, and an expired link.
INSERT INTO gifting_addresses (id, creator_id, campaign_id, recipient_name, street, street2,
                               city, county, postal_code, country, phone,
                               gdpr_consent_flag, consent_source, capture_token,
                               token_expires_at, requested_at, captured_at, consented_at)
VALUES
  ('a2000000-0000-0000-0000-000000000001', 'c5555555-5555-5555-5555-555555555555',
   'e1000000-0000-0000-0000-000000000001', 'Chloe Glow', '18 Mill Lane', 'Flat 2',
   'Leeds', 'West Yorkshire', 'LS1 4DT', 'UK', '07700 900123',
   true, 'SELF_SERVE', NULL, NULL, now() - interval '18 days', now() - interval '17 days', now() - interval '17 days'),

  ('a2000000-0000-0000-0000-000000000002', 'c7777777-7777-7777-7777-777777777777',
   'e1000000-0000-0000-0000-000000000001', 'Emma Cooks', '5 Rye Street', NULL,
   'Manchester', 'Greater Manchester', 'M4 1HN', 'UK', NULL,
   true, 'SELF_SERVE', NULL, NULL, now() - interval '18 days', now() - interval '16 days', now() - interval '16 days'),

  -- Asked 3 days ago, not yet filled in. Live link — usable for a demo.
  ('a2000000-0000-0000-0000-000000000003', 'c6666666-6666-6666-6666-666666666666',
   'e1000000-0000-0000-0000-000000000001', NULL, NULL, NULL, NULL, NULL, NULL, 'UK', NULL,
   false, 'IMPORTED', 'demo-address-token-david-0000000000000000',
   now() + interval '27 days', now() - interval '3 days', NULL, now() - interval '3 days'),

  -- Expired link: opening it must say so rather than 500.
  ('a2000000-0000-0000-0000-000000000004', 'cd000000-0000-0000-0000-00000000000d',
   'e1000000-0000-0000-0000-000000000001', NULL, NULL, NULL, NULL, NULL, NULL, 'UK', NULL,
   false, 'IMPORTED', 'demo-address-token-expired-000000000000',
   now() - interval '1 day', now() - interval '40 days', NULL, now() - interval '40 days')
ON CONFLICT DO NOTHING;

-- Dispatches. The two DELIVERED rows have deadlines landing exactly where the
-- reminder job looks, so running the reminder pass actually sends something.
INSERT INTO dispatches (id, gifting_run_id, creator_id, brand_id, product_name, sku,
                        packaging_notes, courier, tracking_number, status,
                        planned_dispatch_date, content_deadline, shipped_at, delivered_at,
                        return_reason, reminder_week_sent_at, reminder_48h_sent_at,
                        created_at, updated_at)
VALUES
  -- Deadline in exactly 7 days: the week-out reminder is due on the next pass.
  ('a3000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
   'c5555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', 'Tissue wrap, comp slip on top',
   'Royal Mail', 'RM-DEMO-0001', 'DELIVERED',
   CURRENT_DATE - 16, CURRENT_DATE + 7, now() - interval '16 days', now() - interval '14 days',
   NULL, NULL, NULL, now() - interval '18 days', now()),

  -- Deadline in 2 days: the 48-hour reminder is due, and the week-out one has
  -- already gone, so it must not be sent twice.
  ('a3000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
   'c7777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', NULL,
   'Royal Mail', 'RM-DEMO-0002', 'DELIVERED',
   CURRENT_DATE - 16, CURRENT_DATE + 2, now() - interval '16 days', now() - interval '13 days',
   NULL, now() - interval '5 days', NULL, now() - interval '18 days', now()),

  ('a3000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001',
   'c1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', NULL,
   'Royal Mail', 'RM-DEMO-0003', 'DISPATCHED',
   CURRENT_DATE - 2, CURRENT_DATE + 18, now() - interval '2 days', NULL,
   NULL, NULL, NULL, now() - interval '4 days', now()),

  ('a3000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001',
   'c3333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', NULL,
   'Royal Mail', NULL, 'READY_TO_DISPATCH',
   CURRENT_DATE + 1, CURRENT_DATE + 25, NULL, NULL,
   NULL, NULL, NULL, now() - interval '1 day', now()),

  -- Returned undelivered, and refused outright. Both creators are excluded below.
  ('a3000000-0000-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000001',
   'c4444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', NULL,
   'DPD', 'DPD-DEMO-0005', 'RETURNED',
   CURRENT_DATE - 16, CURRENT_DATE + 4, now() - interval '16 days', NULL,
   'Address incomplete, returned to sender', NULL, NULL, now() - interval '18 days', now()),

  ('a3000000-0000-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000001',
   'cb000000-0000-0000-0000-00000000000b', '11111111-1111-1111-1111-111111111111',
   'Mediheal Hydrating Mask Box', 'MED-HYD-01', NULL,
   'Royal Mail', NULL, 'DECLINED',
   CURRENT_DATE - 20, NULL, NULL, NULL,
   'Asked not to receive unsolicited product', NULL, NULL, now() - interval '22 days', now())
ON CONFLICT DO NOTHING;

-- Liam's parcel came back, so he is excluded from future gifting too.
UPDATE creators
SET gifting_excluded = true,
    gifting_exclusion_reason = 'Address incomplete, returned to sender'
WHERE id = 'c4444444-4444-4444-4444-444444444444' AND NOT gifting_excluded;

-- Direct-from-brand orders: one waiting on the brand, one they have confirmed.
INSERT INTO brand_orders (id, brand_id, campaign_id, gifting_run_id, brand_contact_email,
                          product_name, recipient_count, notes, status, confirm_token,
                          confirmed_at, created_at, updated_at)
VALUES
  ('a4000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000002',
   'fulfilment@katieloxton.example', 'Katie Loxton Autumn Tote', 4,
   'Please include the autumn card insert.', 'REQUESTED',
   'demo-brand-order-token-000000000000000000', NULL, now() - interval '4 days', now()),

  ('a4000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
   'fulfilment@mediheal.example', 'Mediheal Hydrating Mask Box', 6,
   NULL, 'CONFIRMED', 'demo-brand-order-token-confirmed-00000000',
   now() - interval '15 days', now() - interval '17 days', now())
ON CONFLICT DO NOTHING;

-- Send history — what reconciliation compares coverage against.
INSERT INTO creator_send_history (id, creator_id, brand_id, campaign_id, sent_at,
                                  duplicate_flag, send_type, product_name, reference_id)
VALUES
  ('a5000000-0000-0000-0000-000000000001', 'c5555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '16 days', false, 'GIFT', 'Mediheal Hydrating Mask Box', 'a3000000-0000-0000-0000-000000000001'),
  ('a5000000-0000-0000-0000-000000000002', 'c7777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '16 days', false, 'GIFT', 'Mediheal Hydrating Mask Box', 'a3000000-0000-0000-0000-000000000002'),
  ('a5000000-0000-0000-0000-000000000003', 'c1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '2 days',  false, 'GIFT', 'Mediheal Hydrating Mask Box', 'a3000000-0000-0000-0000-000000000003'),
  ('a5000000-0000-0000-0000-000000000004', 'c3333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '1 day',   false, 'GIFT', 'Mediheal Hydrating Mask Box', 'a3000000-0000-0000-0000-000000000004'),
  ('a5000000-0000-0000-0000-000000000005', 'c4444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '16 days', false, 'GIFT', 'Mediheal Hydrating Mask Box', 'a3000000-0000-0000-0000-000000000005'),
  ('a5000000-0000-0000-0000-000000000006', 'c6666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '12 days', false, 'EMAIL', NULL, NULL),
  ('a5000000-0000-0000-0000-000000000007', 'c2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'e1000000-0000-0000-0000-000000000001', now() - interval '12 days', false, 'EMAIL', NULL, NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- coverage ---
-- Dated inside the last month so a monthly report picks all of it up.

INSERT INTO coverage_items (id, brand_id, campaign_id, creator_id, creator_handle, platform,
                            post_type, content_form, url, caption, views, likes, comments,
                            shares, saves, impressions, er, standardized_name,
                            is_unsolicited, source, posted_at, created_at, updated_at)
VALUES
  ('b1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c5555555-5555-5555-5555-555555555555', 'chloeglow',
   'INSTAGRAM', 'REEL', 'SHORT', 'https://instagram.com/p/demo-chloe-reel-1',
   'My 10 minute evening routine with the new Mediheal mask', 128000, 9400, 412, 260, 880, NULL,
   8.56, 'b-the-agency_chloeglow_instagram_reel_demo', false, 'AUTO_CLIP',
   now() - interval '12 days', now() - interval '12 days', now()),

  ('b1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c7777777-7777-7777-7777-777777777777', 'emmacooks',
   'TIKTOK', 'TIKTOK', 'SHORT', 'https://tiktok.com/@emmacooks/video/demo-1',
   'Unboxing this skincare set', 96000, 7200, 305, 410, 520, NULL,
   8.79, 'b-the-agency_emmacooks_tiktok_tiktok_demo', false, 'AUTO_CLIP',
   now() - interval '9 days', now() - interval '9 days', now()),

  -- Long form. The short/long split in the report depends on this.
  ('b1000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c6666666-6666-6666-6666-666666666666', 'davidtech',
   'YOUTUBE', 'YOUTUBE', 'LONG', 'https://youtube.com/watch?v=demo-davidtech-1',
   'I tested 12 sheet masks for a month', 54000, 3100, 640, 90, 210, NULL,
   7.48, 'b-the-agency_davidtech_youtube_youtube_demo', false, 'MANUAL',
   now() - interval '6 days', now() - interval '6 days', now()),

  -- Unsolicited: nobody sent to her, she posted anyway.
  ('b1000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'cd000000-0000-0000-0000-00000000000d', 'islareads',
   'TIKTOK', 'TIKTOK', 'SHORT', 'https://tiktok.com/@islareads/video/demo-unsolicited',
   'not sponsored but this mask is genuinely lovely', 18500, 2180, 190, 140, 300, NULL,
   14.65, 'b-the-agency_islareads_tiktok_tiktok_demo', true, 'MENTION',
   now() - interval '4 days', now() - interval '4 days', now()),

  -- Viral. Comfortably past INT range — proves the columns really are BIGINT.
  ('b1000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'ce000000-0000-0000-0000-00000000000e', 'jayglobal',
   'YOUTUBE', 'YOUTUBE', 'LONG', 'https://youtube.com/watch?v=demo-jayglobal-viral',
   'This blew up overnight', 3120000000, 41000000, 890000, 2600000, 5100000, NULL,
   1.58, 'b-the-agency_jayglobal_youtube_youtube_demo', true, 'MENTION',
   now() - interval '3 days', now() - interval '3 days', now()),

  -- Zero views. Any engagement-rate maths must not divide by zero.
  ('b1000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c1111111-1111-1111-1111-111111111111', 'sophiabeauty',
   'INSTAGRAM', 'STORY', 'SHORT', 'https://instagram.com/stories/demo-sophia-story',
   'Story with no view data returned yet', 0, 0, 0, 0, 0, NULL,
   0.00, 'b-the-agency_sophiabeauty_instagram_story_demo', false, 'AUTO_CLIP',
   now() - interval '1 day', now() - interval '1 day', now()),

  -- Belongs to the completed campaign, so the wrap report is not empty.
  ('b1000000-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000003', 'c2222222-2222-2222-2222-222222222222', 'marcuslifts',
   'INSTAGRAM', 'REEL', 'SHORT', 'https://instagram.com/p/demo-spring-marcus',
   'Spring skincare for training days', 74000, 5100, 288, 180, 410, NULL,
   8.08, 'b-the-agency_marcuslifts_instagram_reel_spring', false, 'MANUAL',
   now() - interval '75 days', now() - interval '75 days', now())
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- insights ---
-- One of each state, so the chase screen shows real variety.

INSERT INTO insight_requests (id, brand_id, campaign_id, creator_id, status, chase_count,
                              last_chased_at, received_at, created_at, updated_at)
VALUES
  ('b2000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c1111111-1111-1111-1111-111111111111',
   'PENDING', 0, NULL, NULL, now() - interval '5 days', now()),

  ('b2000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c3333333-3333-3333-3333-333333333333',
   'CHASED', 2, now() - interval '2 days', NULL, now() - interval '8 days', now()),

  ('b2000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c5555555-5555-5555-5555-555555555555',
   'RECEIVED', 1, now() - interval '6 days', now() - interval '5 days', now() - interval '9 days', now()),

  -- Chased four times and still nothing. The one that needs a human.
  ('b2000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', 'c4444444-4444-4444-4444-444444444444',
   'CHASED', 4, now() - interval '1 day', NULL, now() - interval '14 days', now())
ON CONFLICT DO NOTHING;

-- ----------------------------------------------------------------- reports ---
-- One report in each lifecycle state. Metrics are left NULL so pressing
-- "Regenerate figures" computes them from the coverage above — a report showing
-- invented numbers would defeat the point of the honesty rules.

INSERT INTO reports (id, brand_id, campaign_id, template_id, name, report_type, cadence,
                     period_start, period_end, status, metrics,
                     submitted_by, submitted_at, approved_by, approved_at, sent_at,
                     rejection_reason, created_at, updated_at)
VALUES
  ('b3000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', NULL,
   'August seeding — draft', 'MONTHLY_SEEDING', 'MONTHLY',
   CURRENT_DATE - 30, CURRENT_DATE, 'DRAFT', NULL,
   NULL, NULL, NULL, NULL, NULL, NULL, now() - interval '1 day', now()),

  ('b3000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', NULL,
   'August seeding — awaiting sign-off', 'MONTHLY_SEEDING', 'MONTHLY',
   CURRENT_DATE - 30, CURRENT_DATE - 1, 'PENDING_APPROVAL', NULL,
   '44444444-4444-4444-4444-444444444444', now() - interval '2 days',
   NULL, NULL, NULL, NULL, now() - interval '3 days', now()),

  -- Sent back by the director, with the reason the account manager sees.
  ('b3000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000001', NULL,
   'August seeding — changes requested', 'MONTHLY_SEEDING', 'MONTHLY',
   CURRENT_DATE - 45, CURRENT_DATE - 15, 'REJECTED', NULL,
   '44444444-4444-4444-4444-444444444444', now() - interval '6 days',
   NULL, NULL, NULL,
   'Please split the unsolicited coverage out of the headline reach figure before this goes over.',
   now() - interval '7 days', now()),

  ('b3000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000003', NULL,
   'Spring Skincare Push — campaign wrap', 'CAMPAIGN_WRAP', 'CAMPAIGN',
   CURRENT_DATE - 120, CURRENT_DATE - 60, 'APPROVED', NULL,
   '44444444-4444-4444-4444-444444444444', now() - interval '50 days',
   '33333333-3333-3333-3333-333333333333', now() - interval '49 days', NULL,
   NULL, now() - interval '52 days', now()),

  -- Already with the client. Regenerating this must be refused.
  ('b3000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
   'e1000000-0000-0000-0000-000000000003', NULL,
   'Spring Skincare Push — sent to client', 'MAILER_CONVERSION', 'AD_HOC',
   CURRENT_DATE - 120, CURRENT_DATE - 60, 'SENT', NULL,
   '44444444-4444-4444-4444-444444444444', now() - interval '48 days',
   '33333333-3333-3333-3333-333333333333', now() - interval '47 days',
   now() - interval '46 days', NULL, now() - interval '50 days', now())
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------ coverage settings ----
-- Switch the digest on for the workspace brand so the morning email has settings
-- to run against.

INSERT INTO coverage_digest_settings (id, brand_id, enabled, send_time, recipient_email,
                                      clipping_name_pattern, include_unsolicited)
VALUES ('b4000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
        true, '08:00', NULL, '{brand}_{handle}_{platform}_{type}_{date}', true)
ON CONFLICT (brand_id) DO NOTHING;
