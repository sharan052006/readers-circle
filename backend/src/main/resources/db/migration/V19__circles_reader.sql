-- V19: Circles reader screens (my/explore/requests/detail/events/organizers/members)
-- 1. circles.tags (multi-tag, comma-separated; keeps category for compat)
-- 2. circle_organizers (co-organizers + past-organizer history)
-- 3. users.city + users.interests (member/organizer cards)

ALTER TABLE circles ADD COLUMN IF NOT EXISTS tags VARCHAR(1000) NULL;

CREATE TABLE IF NOT EXISTS circle_organizers (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  circle_id   UUID            NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  user_id     UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  added_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
  removed_at  TIMESTAMPTZ     NULL,
  CONSTRAINT ux_circle_organizer UNIQUE (circle_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_circle_organizers_circle ON circle_organizers (circle_id, removed_at);

-- Backfill: every circle's primary organizer becomes a current co-organizer row.
INSERT INTO circle_organizers (circle_id, user_id)
SELECT c.id, c.organizer_id FROM circles c
WHERE NOT EXISTS (
  SELECT 1 FROM circle_organizers o WHERE o.circle_id = c.id AND o.user_id = c.organizer_id
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS city VARCHAR(120) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS interests VARCHAR(1000) NULL;
