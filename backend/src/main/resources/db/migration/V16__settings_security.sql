-- V16: Settings backend (security + notification preferences).
-- Password/email/photo need no schema (users row already holds them).

-- 2FA TOTP + refresh-session versioning on users.
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

-- Per-user notification preferences (one row per user, created on first access).
CREATE TABLE IF NOT EXISTS notification_preferences (
  user_id               UUID            PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  join_request          BOOLEAN         NOT NULL DEFAULT TRUE,
  member_joined         BOOLEAN         NOT NULL DEFAULT TRUE,
  event_created         BOOLEAN         NOT NULL DEFAULT TRUE,
  event_registration    BOOLEAN         NOT NULL DEFAULT TRUE,
  book_recommendation   BOOLEAN         NOT NULL DEFAULT TRUE,
  discussion            BOOLEAN         NOT NULL DEFAULT TRUE,
  report                BOOLEAN         NOT NULL DEFAULT TRUE,
  updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Refresh sessions backing Active Sessions + Sign out of all devices.
CREATE TABLE IF NOT EXISTS refresh_sessions (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash    VARCHAR(64)     NOT NULL,
  device_info   VARCHAR(500)    NULL,
  ip_address    VARCHAR(100)    NULL,
  revoked       BOOLEAN         NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
  last_used_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_refresh_sessions_user ON refresh_sessions (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_refresh_sessions_hash ON refresh_sessions (token_hash);
