-- V22: Reader Settings — add Chat messages + Replies toggles.
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS chat_message BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS replies BOOLEAN NOT NULL DEFAULT TRUE;
