-- V20: Reader Events screens (upcoming/registered/past/detail)
-- 1. events.type + events.mode (Event Type / Location filter panels)
-- 2. event_registrations.attended (Attended / Did Not Attend + stats)
-- 3. event_feedbacks (past-event review quotes, one per reader per event)
-- 4. event_messages (Event Chat board on the event detail screen)

ALTER TABLE events ADD COLUMN IF NOT EXISTS type VARCHAR(40) NULL;
ALTER TABLE events ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NULL;

ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS attended BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS attended_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS event_feedbacks (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id    UUID            NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  reader_id   UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating      INTEGER         NULL CHECK (rating BETWEEN 1 AND 5),
  content     TEXT            NOT NULL,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ux_event_feedback UNIQUE (event_id, reader_id)
);
CREATE INDEX IF NOT EXISTS ix_event_feedbacks_event ON event_feedbacks (event_id, created_at DESC);

CREATE TABLE IF NOT EXISTS event_messages (
  id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id    UUID            NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  author_id   UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content     TEXT            NOT NULL,
  created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_event_messages_event ON event_messages (event_id, created_at ASC);
