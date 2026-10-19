-- V18: Reader dashboard missing parts
-- 1. discussions.tags (multi-tag support, comma-separated). Keeps legacy `tag` for compat.
-- 2. notifications inbox (bell) backing GET /api/notifications.

ALTER TABLE discussions ADD COLUMN IF NOT EXISTS tags VARCHAR(1000) NULL;

CREATE TABLE IF NOT EXISTS notifications (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type        VARCHAR(60)     NOT NULL DEFAULT 'GENERAL',
  title       VARCHAR(255)    NOT NULL,
  body        TEXT            NULL,
  link        VARCHAR(1000)   NULL,
  is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notifications_user_unread ON notifications (user_id, is_read, created_at DESC);
