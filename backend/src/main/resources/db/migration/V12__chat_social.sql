-- V12: Chat social backend for General Chat UI (likes, read-state, attachments, hidden lounge circle)

-- Message image attachments (image URL served from /api/media)
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS attachment_url VARCHAR(1000);

-- Message likes (heart counts on bubbles)
CREATE TABLE IF NOT EXISTS chat_message_likes (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id    UUID            NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
  user_id       UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_chat_message_like UNIQUE (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_chat_like_message ON chat_message_likes (message_id);
CREATE INDEX IF NOT EXISTS ix_chat_like_user ON chat_message_likes (user_id);

-- Per-user per-circle read tracking (unread badges)
CREATE TABLE IF NOT EXISTS chat_read_state (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  circle_id     UUID            NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  reader_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  last_read_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_chat_read_state UNIQUE (circle_id, reader_id)
);
CREATE INDEX IF NOT EXISTS ix_chat_read_reader ON chat_read_state (reader_id);

-- Hidden circles (Organisers Lounge is invisible to readers in lists/detail)
ALTER TABLE circles ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS ix_circles_hidden ON circles (hidden);
