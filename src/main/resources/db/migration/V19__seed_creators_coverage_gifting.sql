-- Demo seed data. All identifiers below are valid hexadecimal UUIDs.
-- (The previous revision of this file used literals such as 't1111111-…' / 's1111111-…'
--  which are not valid UUIDs and aborted the whole Flyway run.)
--
-- Shortlist seed data lives in V20, which is where the shortlist tables are created.

-- Seed Creators
INSERT INTO creators (id, brand_id, name, handle, email, phone, primary_platform, followers_count, er_percentage, location, niche, opt_in_status)
VALUES
    ('c1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Sophia Styles', 'sophiabeauty', 'sophia@creators.com', '+44 7111 111111', 'INSTAGRAM', 45000, 4.20, 'London, UK', 'Beauty', 'APPROVED'),
    ('c2222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Marcus Lifts', 'marcuslifts', 'marcus@creators.com', '+44 7222 222222', 'INSTAGRAM', 120000, 3.80, 'Manchester, UK', 'Fitness', 'APPROVED'),
    ('c3333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Ella Fashion', 'ellafashion', 'ella@creators.com', '+44 7333 333333', 'TIKTOK', 89000, 5.10, 'London, UK', 'Fashion', 'APPROVED'),
    ('c4444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Liam Travel', 'liamtravels', 'liam@creators.com', '+44 7444 444444', 'YOUTUBE', 210000, 2.90, 'Edinburgh, UK', 'Travel', 'APPROVED'),
    ('c5555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Chloe Glow', 'chloeglow', 'chloe@creators.com', '+44 7555 555555', 'INSTAGRAM', 62000, 4.80, 'Bristol, UK', 'Skincare', 'APPROVED'),
    ('c6666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'David Tech', 'davidtech', 'david@creators.com', '+44 7666 666666', 'YOUTUBE', 310000, 3.40, 'Cambridge, UK', 'Tech', 'APPROVED'),
    ('c7777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Emma Cooks', 'emmacooks', 'emma@creators.com', '+44 7777 777777', 'TIKTOK', 175000, 6.20, 'Leeds, UK', 'Food', 'APPROVED'),
    ('c8888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111', 'Noah Wellness', 'noahwellness', 'noah@creators.com', '+44 7888 888888', 'INSTAGRAM', 34000, 4.00, 'Brighton, UK', 'Wellness', 'PENDING_REVIEW')
ON CONFLICT (id) DO NOTHING;

-- Seed Content Style Tags  ('t…' -> '7a…')
INSERT INTO content_style_tags (id, brand_id, name)
VALUES
    ('7a111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Minimalist'),
    ('7a222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Vibrant / High Energy'),
    ('7a333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Clean Girl Aesthetic'),
    ('7a444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'GRWM / Vlogs'),
    ('7a555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Elevated'),
    ('7a666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'Editorial')
ON CONFLICT (name) DO NOTHING;

-- Seed Coverage Items  ('v…' -> '0a…')
INSERT INTO coverage_items (id, brand_id, creator_id, creator_handle, platform, post_type, url, views, likes, comments, er, standardized_name, is_unsolicited)
VALUES
    ('0a111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'sophiabeauty', 'INSTAGRAM', 'REEL', 'https://instagram.com/p/reel_sophiabeauty', 42000, 3100, 142, 4.20, 'sophiastyles-sophiabeauty-reel-2026-08-15', false),
    ('0a222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222', 'marcuslifts', 'INSTAGRAM', 'POST', 'https://instagram.com/p/post_marcuslifts', 18500, 1950, 89, 3.80, 'marcuslifts-marcuslifts-post-2026-08-14', false),
    ('0a333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'c3333333-3333-3333-3333-333333333333', 'ellafashion', 'TIKTOK', 'TIKTOK', 'https://tiktok.com/@ellafashion/video/1', 89000, 8200, 410, 5.10, 'ellafashion-ellafashion-tiktok-2026-08-16', true)
ON CONFLICT (id) DO NOTHING;

-- Seed Gifting Runs & Dispatches  ('g…' -> '6a…')
INSERT INTO gifting_runs (id, brand_id, mailer_text, comp_slip_status)
VALUES
    ('6a111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Thank you for being part of our Mediheal Spring Seeding family!', 'APPROVED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO gifting_addresses (id, creator_id, street, city, postal_code, country, gdpr_consent_flag)
VALUES
    ('a1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', '12 Oxford Street', 'London', 'W1D 1BS', 'UK', true),
    ('a2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', '45 Deansgate', 'Manchester', 'M3 2AY', 'UK', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO dispatches (id, gifting_run_id, creator_id, tracking_number, courier, status)
VALUES
    ('d1111111-1111-1111-1111-111111111111', '6a111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'RM-9901-UK', 'Royal Mail', 'DELIVERED'),
    ('d2222222-2222-2222-2222-222222222222', '6a111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222', 'RM-9902-UK', 'Royal Mail', 'DISPATCHED'),
    ('d3333333-3333-3333-3333-333333333333', '6a111111-1111-1111-1111-111111111111', 'c3333333-3333-3333-3333-333333333333', 'RM-9903-UK', 'DPD', 'RETURNED')
ON CONFLICT (id) DO NOTHING;
