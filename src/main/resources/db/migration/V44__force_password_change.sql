-- Forced password change on first login.
--
-- V15 seeded four accounts with the password `Password123!`, and that password is printed in
-- the README. Those rows are not local-only: V15 is an ordinary migration, so every environment
-- it has ever run against — including production — has them. Deactivating them outright would
-- leave a client handover with no way in, so instead the credential becomes single-use: it
-- still works once, and the session it opens can do nothing except set a real password.
--
-- The flag is a general mechanism, not a one-off. Any account created with a known or shared
-- password can be marked this way.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;

-- The four seeded accounts, reset to a known state for handover.
--
-- The password is set explicitly rather than assumed: if someone changed one of these by hand
-- in a deployed environment, this puts every environment back on the same footing. Nothing is
-- lost by doing so, because the very next login has to set a new password anyway.
--
-- Hash is BCrypt (cost 10) of `Password123!` — the same value V15 used.
UPDATE users
SET password             = '$2a$10$xiLK4IIk4obs1oU2sXyqIuHeEfvZ3qxT/bYmGiKIeKAkKpk4nJRta',
    must_change_password = true,
    password_changed_at  = NULL,
    failed_login_count   = 0,
    locked_until         = NULL
WHERE email IN (
    'admin@generationb.dev',
    'director@generationb.dev',
    'am@generationb.dev',
    'ae@generationb.dev'
);

-- Any session opened with the old credential is dead. Otherwise a token minted before this
-- migration would keep working and never meet the prompt.
UPDATE refresh_tokens
SET revoked_at = now()
WHERE revoked_at IS NULL
  AND user_id IN (SELECT id FROM users WHERE must_change_password);

COMMENT ON COLUMN users.must_change_password IS
    'The account is on a temporary password. Every request except change-password, me and '
    'logout is refused until the user sets their own.';
