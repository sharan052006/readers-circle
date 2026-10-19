-- V7: Citations module table and initial literary citations
CREATE TABLE citations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    quote_snippet TEXT NOT NULL,
    source_url VARCHAR(500),
    circle_id UUID REFERENCES circles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_citations_circle_id ON citations(circle_id);

INSERT INTO citations (book_title, author, quote_snippet, source_url) VALUES
('To Kill a Mockingbird', 'Harper Lee', 'You never really understand a person until you consider things from his point of view... until you climb into his skin and walk around in it.', 'https://en.wikipedia.org/wiki/To_Kill_a_Mockingbird'),
('The Great Gatsby', 'F. Scott Fitzgerald', 'So we beat on, boats against the current, borne back ceaselessly into the past.', 'https://en.wikipedia.org/wiki/The_Great_Gatsby'),
('One Hundred Years of Solitude', 'Gabriel García Márquez', 'There is always something left to love.', 'https://en.wikipedia.org/wiki/One_Hundred_Years_of_Solitude'),
('Pride and Prejudice', 'Jane Austen', 'I declare after all there is no enjoyment like reading! How much sooner one tires of any thing than of a book!', 'https://en.wikipedia.org/wiki/Pride_and_Prejudice'),
('The Alchemist', 'Paulo Coelho', 'When you want something, all the universe conspires in helping you to achieve it.', 'https://en.wikipedia.org/wiki/The_Alchemist_(novel)'),
('Dune', 'Frank Herbert', 'I must not fear. Fear is the mind-killer. Fear is the little-death that brings total obliteration.', 'https://en.wikipedia.org/wiki/Dune_(novel)');
