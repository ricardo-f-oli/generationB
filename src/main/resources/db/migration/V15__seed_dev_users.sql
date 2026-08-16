INSERT INTO brands (id, name)
VALUES ('11111111-1111-1111-1111-111111111111', 'Default Brand')
ON CONFLICT (id) DO NOTHING;

-- Password for all seeded users is: Password123!
-- Real BCrypt (cost 10) hash, verified against BCrypt.checkpw.
INSERT INTO users (id, brand_id, name, username, email, password, role, active)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Admin User', 'admin', 'admin@generationb.dev', '$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta', 'ADMIN', true),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Director User', 'director', 'director@generationb.dev', '$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta', 'DIRECTOR', true),
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'Account Manager', 'am', 'am@generationb.dev', '$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta', 'ACCOUNT_MANAGER', true),
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'Account Executive', 'ae', 'ae@generationb.dev', '$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta', 'ACCOUNT_EXECUTIVE', true)
ON CONFLICT (email) DO NOTHING;
