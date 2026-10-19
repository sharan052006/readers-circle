-- V5: chat_messages table (SPEC-circle-chat.md contracts)

CREATE TABLE chat_messages (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  circle_id     UUID            NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  sender_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content       TEXT            NOT NULL CHECK (char_length(btrim(content)) > 0 AND char_length(content) <= 2000),
  sent_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
  edited_at     TIMESTAMPTZ     NULL,
  deleted       BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_chat_messages_circle_sent_at ON chat_messages (circle_id, sent_at ASC);
CREATE INDEX ix_chat_messages_sender ON chat_messages (sender_id);
