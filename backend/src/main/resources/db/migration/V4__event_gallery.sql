-- V4: event_gallery_items table (SPEC-event-gallery.md contracts)

CREATE TABLE event_gallery_items (
  id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
  event_id      UUID            NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  uploaded_by   UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  media_type    VARCHAR(20)     NOT NULL CHECK (media_type IN ('PHOTO', 'VIDEO')),
  media_url     VARCHAR(1000)   NOT NULL CHECK (char_length(btrim(media_url)) > 0),
  caption       VARCHAR(255),
  uploaded_at   TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX ix_gallery_items_event ON event_gallery_items (event_id, uploaded_at);
CREATE INDEX ix_gallery_items_uploader ON event_gallery_items (uploaded_by);
