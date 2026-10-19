-- V26: Optional organiser notes for attendees ("Event Guidelines" in the UI).
ALTER TABLE events ADD COLUMN guidelines VARCHAR(1000);
