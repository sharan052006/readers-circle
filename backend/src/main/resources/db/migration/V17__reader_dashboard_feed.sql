-- V17: Reader dashboard feed enhancements
-- Adds circle_id and likes_count to discussions, and creates discussion_likes table.

ALTER TABLE discussions ADD COLUMN IF NOT EXISTS circle_id UUID REFERENCES circles(id) ON DELETE SET NULL;
ALTER TABLE discussions ADD COLUMN IF NOT EXISTS likes_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS ix_discussions_circle_id ON discussions (circle_id);

CREATE TABLE IF NOT EXISTS discussion_likes (
  id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  discussion_id  UUID            NOT NULL REFERENCES discussions(id) ON DELETE CASCADE,
  user_id        UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_discussion_like UNIQUE (discussion_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_discussion_likes_discussion ON discussion_likes (discussion_id);
