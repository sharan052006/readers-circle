/**
 * Standard default cover art shared by every login (reader, organiser, admin)
 * so placeholder imagery is identical no matter who is signed in.
 */

const enc = (svg: string) => 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);

/** Default cover for events and circles (branded burgundy "Reader's Circle"). */
export const COVER_PLACEHOLDER = enc(
  '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="260" viewBox="0 0 400 260">' +
    '<defs>' +
    '<linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#58241A"/><stop offset="100%" stop-color="#2D120C"/></linearGradient>' +
    '</defs>' +
    '<rect width="400" height="260" rx="10" fill="url(#bg)"/>' +
    '<rect x="18" y="18" width="364" height="224" rx="6" fill="none" stroke="#D8B168" stroke-width="1.2" stroke-dasharray="4 3" opacity="0.45"/>' +
    '<circle cx="200" cy="105" r="32" fill="#6A2E20" stroke="#D8B168" stroke-width="1.5" opacity="0.9"/>' +
    '<path d="M190 100c2.5-2.5 6-3.5 10-3.5s7.5 1 10 3.5v14c-2.5-2-6-2.5-10-2.5s-7.5 .5-10 2.5z M200 100v14" fill="none" stroke="#FAF2E6" stroke-width="1.8" stroke-linecap="round"/>' +
    '<text x="200" y="165" font-family="Georgia, serif" font-size="17" font-weight="bold" text-anchor="middle" fill="#FAF2E6" letter-spacing="1">READER\u2019S CIRCLE</text>' +
    '<text x="200" y="186" font-family="sans-serif" font-size="10" text-anchor="middle" fill="#DEC09A" letter-spacing="2">COMMUNITY CLUB</text>' +
    '</svg>',
);

/** Default cover for books / store items. */
export const BOOK_PLACEHOLDER = enc(
  '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="260"><rect width="100%" height="100%" fill="#EFE6D2"/><text x="50%" y="52%" font-family="Georgia" font-size="20" text-anchor="middle" fill="#6B2A1F">No cover</text></svg>',
);

/** Default cover for book discussions (tall book shape). */
export const DISCUSSION_COVER_PLACEHOLDER = enc(
  '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="420" viewBox="0 0 300 420">' +
    '<defs>' +
    '<linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#542318"/><stop offset="100%" stop-color="#2C120C"/></linearGradient>' +
    '<linearGradient id="spine" x1="0%" y1="0%" x2="100%" y2="0%"><stop offset="0%" stop-color="rgba(255,255,255,0.22)"/><stop offset="35%" stop-color="rgba(0,0,0,0.18)"/><stop offset="100%" stop-color="transparent"/></linearGradient>' +
    '</defs>' +
    '<rect width="300" height="420" rx="8" fill="url(#bg)"/>' +
    '<rect width="24" height="420" fill="url(#spine)"/>' +
    '<rect x="24" y="24" width="252" height="372" rx="4" fill="none" stroke="#D8B168" stroke-width="1.2" stroke-dasharray="4 3" opacity="0.5"/>' +
    '<circle cx="150" cy="180" r="36" fill="#6A2E20" stroke="#D8B168" stroke-width="1.5" opacity="0.9"/>' +
    '<path d="M138 174c3-3 7-4 12-4s9 1 12 4v16c-3-2-7-3-12-3s-9 1-12 3z M150 174v16" fill="none" stroke="#FAF2E6" stroke-width="2" stroke-linecap="round"/>' +
    '<text x="150" y="250" font-family="Georgia, serif" font-size="18" font-weight="bold" text-anchor="middle" fill="#FAF2E6" letter-spacing="1">READER\u2019S CIRCLE</text>' +
    '<text x="150" y="274" font-family="sans-serif" font-size="11" text-anchor="middle" fill="#DEC09A" letter-spacing="2">BOOK CLUB</text>' +
    '</svg>',
);
