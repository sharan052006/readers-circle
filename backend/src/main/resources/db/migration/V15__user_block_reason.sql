-- V15: Block-user reason + timestamp (Block User modal "Reason (optional)").
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMPTZ NULL;
