-- V21: Reader Chat screen (reactions, previews, search)
-- 1. chat_message_likes.emoji (multi-emoji reactions; existing hearts backfilled)

ALTER TABLE chat_message_likes ADD COLUMN IF NOT EXISTS emoji VARCHAR(16) NOT NULL DEFAULT '❤';
ALTER TABLE chat_message_likes DROP CONSTRAINT IF EXISTS ux_chat_message_like;
ALTER TABLE chat_message_likes
  ADD CONSTRAINT ux_chat_message_like_emoji UNIQUE (message_id, user_id, emoji);
