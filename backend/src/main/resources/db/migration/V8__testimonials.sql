-- V8: Testimonials module table and initial community testimonials
CREATE TABLE testimonials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_name VARCHAR(150) NOT NULL,
    member_role VARCHAR(50) DEFAULT 'Reader',
    profile_photo_url TEXT,
    circle_name VARCHAR(200) NOT NULL,
    quote TEXT NOT NULL,
    rating INT DEFAULT 5,
    approved BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_testimonials_approved ON testimonials(approved);

INSERT INTO testimonials (member_name, member_role, profile_photo_url, circle_name, quote, rating, approved) VALUES
('Aarav Patel', 'Reader', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80', 'Bangalore Fiction & Coffee', 'Reader''s Circle rekindled my habit of reading physical books. Meeting fellow passionate readers in cozy indie cafes every fortnight is the highlight of my month.', 5, TRUE),
('Priya Sharma', 'Circle Organizer', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80', 'Delhi Literary Guild', 'Hosting our circle through Reader''s Circle has been seamless. The RSVP cap management and dedicated circle spaces make book discussions focused and deeply rewarding.', 5, TRUE),
('Rohan Mehta', 'Reader', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=150&q=80', 'Mumbai Classicists', 'The discussions here go far deeper than average online forums. You truly connect with people who treasure the written word and literary nuance.', 5, TRUE),
('Ananya Sen', 'Reader', 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=150&q=80', 'Kolkata Poetry & Essays', 'Finding a welcoming space to discuss philosophy and contemporary fiction in person was so difficult until I discovered Reader''s Circle. Absolutely life-changing!', 5, TRUE);
