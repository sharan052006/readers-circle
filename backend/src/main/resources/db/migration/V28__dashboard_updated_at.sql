-- V28: updated_at for circles and events (backs the Admin Dashboard "Recent Activity" feed).
-- Existing rows default to their created_at so the feed is populated immediately.
ALTER TABLE circles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE circles SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE events SET updated_at = created_at WHERE updated_at IS NULL;
