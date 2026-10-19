-- V23: Nested discussion replies + comment likes for discussion detail view.
-- Adds parent_comment_id + likes_count to discussion_comments,
-- creates discussion_comment_likes for per-user comment like toggles.

ALTER TABLE discussion_comments ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES discussion_comments(id) ON DELETE CASCADE;
ALTER TABLE discussion_comments ADD COLUMN IF NOT EXISTS likes_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS ix_discussion_comments_parent ON discussion_comments (parent_comment_id);

CREATE TABLE IF NOT EXISTS discussion_comment_likes (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  comment_id  UUID            NOT NULL REFERENCES discussion_comments(id) ON DELETE CASCADE,
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_discussion_comment_like UNIQUE (comment_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_discussion_comment_like_comment ON discussion_comment_likes (comment_id);
