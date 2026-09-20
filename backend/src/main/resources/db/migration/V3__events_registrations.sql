-- V3: events and event_registrations tables (SPEC-events-registration.md contracts)

CREATE TABLE events (
  id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  circle_id             UUID            NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
  title                 VARCHAR(200)    NOT NULL CHECK (char_length(btrim(title)) > 0),
  description           TEXT,
  reading_topic         VARCHAR(200),
  event_date            DATE,
  event_time            TIME,
  venue                 VARCHAR(255),
  capacity              INTEGER         CHECK (capacity IS NULL OR capacity > 0),
  registration_deadline TIMESTAMPTZ,
  cover_image_url       VARCHAR(1000),
  status                VARCHAR(20)     NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELLED')),
  created_at            TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_events_circle_status ON events (circle_id, status);
CREATE INDEX ix_events_date ON events (event_date);

CREATE TABLE event_registrations (
  id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id              UUID            NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  reader_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status                VARCHAR(20)     NOT NULL DEFAULT 'REGISTERED'
                        CHECK (status IN ('REGISTERED', 'CANCELLED')),
  registered_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
  cancelled_at          TIMESTAMPTZ
);

-- Active registration partial unique constraint: ensures at most one active registration per (event, reader)
CREATE UNIQUE INDEX ux_event_registrations_active 
ON event_registrations (event_id, reader_id) 
WHERE status = 'REGISTERED';

CREATE INDEX ix_event_registrations_event ON event_registrations (event_id, status);
CREATE INDEX ix_event_registrations_reader ON event_registrations (reader_id);
