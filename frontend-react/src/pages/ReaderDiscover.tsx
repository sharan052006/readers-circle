import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api, getAccess, initialsOf, resolveMedia } from '../lib/api';
import { DISCUSSION_COVER_PLACEHOLDER as PLACEHOLDER } from '../lib/placeholders';
import { MonthCal } from './ReaderEvents';
import './ReaderDiscover.css';

type TrendBook = {
  id?: string | number;
  bookTitle?: string;
  title?: string;
  author?: string;
  discussionCount?: number;
  discussions?: number;
  coverImageUrl?: string;
};

type RecBook = {
  id?: string | number;
  bookTitle?: string;
  title?: string;
  author?: string;
  coverImageUrl?: string;
  coverUrl?: string;
  recommendedByCount?: number;
  distinctRecommenders?: number;
  upvotesCount?: number;
};

type TopRecommender = {
  name?: string;
  recommendationsCount?: number;
  count?: number;
};

type RegEvent = {
  id?: string | number;
  eventId?: string | number;
  title?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  circleName?: string;
  coverImageUrl?: string;
  status?: string;
};

type Membership = {
  id: string;
  circleId: string;
  name: string;
  status: string;
  coverImageUrl?: string;
  memberCount?: number;
};

type FilterCategory = 'all' | 'books' | 'events' | 'circles';

export default function ReaderDiscover() {
  const navigate = useNavigate();
  const [activeCategory, setActiveCategory] = useState<FilterCategory>('all');
  const [sync, setSync] = useState('Connecting…');
  const [loading, setLoading] = useState(true);

  // Books & Recommendations
  const [trending, setTrending] = useState<TrendBook[]>([]);
  const [recommendations, setRecommendations] = useState<RecBook[]>([]);
  const [topRecommenders, setTopRecommenders] = useState<TopRecommender[]>([]);

  // Events & Calendar
  const [calDates, setCalDates] = useState<Record<string, number>>({});
  const [registeredEvents, setRegisteredEvents] = useState<RegEvent[]>([]);
  const [eventStats, setEventStats] = useState({ registered: 0, waitlisted: 0, attended: 0 });

  // Circles & Roles
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [orgMap, setOrgMap] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!getAccess()) {
      navigate('/auth');
      return;
    }

    let isMounted = true;
    async function loadDiscoverData() {
      setSync('Syncing with admin data…');
      setLoading(true);

      // 1. Trending Books
      try {
        const tb = await api<TrendBook[]>('/api/books/trending?limit=6');
        if (isMounted && Array.isArray(tb)) setTrending(tb);
      } catch {
        /* fallback to dashboard if available */
      }

      // 2. Curated Recommendations
      try {
        const recs = await api<RecBook[]>('/api/recommendations/books?limit=6&sort=recommended');
        if (isMounted && Array.isArray(recs)) setRecommendations(recs);
      } catch {
        /* optional */
      }

      // 3. Top Recommenders
      try {
        const top = await api<TopRecommender[]>('/api/recommendations/top-recommenders');
        if (isMounted && Array.isArray(top)) setTopRecommenders(top.slice(0, 5));
      } catch {
        /* optional */
      }

      // 4. Events & Calendar dates
      try {
        const allEvents = await api<Array<{ id: string; eventDate?: string; event_date?: string }>>('/api/events?scope=all');
        if (isMounted && Array.isArray(allEvents)) {
          const dmap: Record<string, number> = {};
          allEvents.forEach((ev) => {
            const raw = ev.eventDate || ev.event_date;
            if (!raw) return;
            const iso = raw.includes('T') ? raw.split('T')[0] : raw.slice(0, 10);
            if (iso) dmap[iso] = (dmap[iso] || 0) + 1;
          });
          setCalDates(dmap);
        }
      } catch {
        /* optional */
      }

      // 5. User Registered Events
      try {
        const regs = await api<RegEvent[]>('/api/users/me/registrations');
        if (isMounted && Array.isArray(regs)) {
          setRegisteredEvents(regs);
          let regCount = 0;
          let waitCount = 0;
          let attCount = 0;
          regs.forEach((r) => {
            const st = (r.status || 'CONFIRMED').toUpperCase();
            if (st === 'ATTENDED') attCount++;
            else if (st === 'WAITLISTED') waitCount++;
            else regCount++;
          });
          setEventStats({ registered: regCount, waitlisted: waitCount, attended: attCount });
        }
      } catch {
        /* optional */
      }

      // 6. User Circles & Roles
      try {
        const mems = await api<Membership[]>('/api/users/me/memberships');
        if (isMounted && Array.isArray(mems)) {
          setMemberships(mems);
        }
        try {
          const orgCircles = await api<Array<{ id: string }>>('/api/users/me/organized-circles');
          if (isMounted && Array.isArray(orgCircles)) {
            const om: Record<string, boolean> = {};
            orgCircles.forEach((c) => {
              if (c.id) om[c.id] = true;
            });
            setOrgMap(om);
          }
        } catch {
          /* optional */
        }
      } catch {
        /* optional */
      }

      if (isMounted) {
        setSync('Synced with admin just now');
        setLoading(false);
      }
    }

    void loadDiscoverData();

    return () => {
      isMounted = false;
    };
  }, [navigate]);

  const approvedMemberships = memberships.filter((m) => m.status === 'APPROVED');
  const pendingRequestsCount = memberships.filter((m) => m.status === 'PENDING').length;
  const declinedRequestsCount = memberships.filter((m) => m.status === 'REJECTED').length;

  return (
    <ReaderLayout active="discover" syncNote={sync}>
      <div className="rdisc-container">
        {/* Hero Header */}
        <header className="rdisc-hero">
          <div className="rdisc-hero-badge">
            <span aria-hidden="true">🧭</span>
            <span>Mobile Hub</span>
          </div>
          <h1 className="rdisc-title">Discover</h1>
          <p className="rdisc-subtitle">
            Explore trending books, community recommendations, upcoming circle events, and your reading activity.
          </p>

          {/* Category Filter Pills */}
          <div className="rdisc-filter-pills" role="tablist" aria-label="Discover sections">
            <button
              type="button"
              role="tab"
              aria-selected={activeCategory === 'all'}
              className={'rdisc-pill' + (activeCategory === 'all' ? ' active' : '')}
              onClick={() => setActiveCategory('all')}
            >
              🌟 All
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeCategory === 'books'}
              className={'rdisc-pill' + (activeCategory === 'books' ? ' active' : '')}
              onClick={() => setActiveCategory('books')}
            >
              📚 Books &amp; Reads
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeCategory === 'events'}
              className={'rdisc-pill' + (activeCategory === 'events' ? ' active' : '')}
              onClick={() => setActiveCategory('events')}
            >
              📅 Events &amp; Calendar
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeCategory === 'circles'}
              className={'rdisc-pill' + (activeCategory === 'circles' ? ' active' : '')}
              onClick={() => setActiveCategory('circles')}
            >
              👥 Circles &amp; Roles
            </button>
          </div>
        </header>

        {loading ? (
          <div className="rdisc-loading">
            <span className="rdisc-spinner" aria-hidden="true" />
            <p>Loading your discovery feed…</p>
          </div>
        ) : (
          <div className="rdisc-content">
            {/* SECTION 1: BOOKS & RECOMMENDATIONS */}
            {(activeCategory === 'all' || activeCategory === 'books') && (
              <section className="rdisc-section" aria-labelledby="sec-books-title">
                <div className="rdisc-section-head">
                  <h2 id="sec-books-title">
                    <span aria-hidden="true">🔥</span> Trending &amp; Recommended Books
                  </h2>
                  <Link to="/reader-books" className="rdisc-section-link">
                    Explore Store →
                  </Link>
                </div>

                {/* Trending Books Grid */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>🔥 Trending Books</h3>
                    <span className="rdisc-badge-pill">Most active</span>
                  </div>
                  {trending.length > 0 ? (
                    <div className="rdisc-book-list">
                      {trending.map((book, idx) => {
                        const title = book.bookTitle || book.title || 'Untitled Book';
                        const discussions = Number(book.discussionCount ?? book.discussions ?? 0);
                        return (
                          <div className="rdisc-book-row" key={book.id || idx}>
                            <span className="rdisc-book-rank">{idx + 1}</span>
                            <img
                              className="rdisc-book-cover"
                              src={book.coverImageUrl || PLACEHOLDER}
                              alt={'Cover of ' + title}
                              onError={(e) => {
                                e.currentTarget.onerror = null;
                                e.currentTarget.src = PLACEHOLDER;
                              }}
                            />
                            <div className="rdisc-book-info">
                              <b className="rdisc-book-title">{title}</b>
                              <span className="rdisc-book-author">by {book.author || 'Unknown'}</span>
                              <span className="rdisc-book-metric">
                                💬 {discussions} {discussions === 1 ? 'discussion' : 'discussions'}
                              </span>
                            </div>
                            <Link to="/reader" className="rdisc-mini-btn" title="View discussions">
                              Join →
                            </Link>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="rdisc-empty-sub">No trending books yet.</p>
                  )}
                </div>

                {/* Curated Recommendations */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>⭐ Community Recommendations</h3>
                    <Link to="/reader-recommendations" className="rdisc-card-sublink">
                      View all →
                    </Link>
                  </div>
                  {recommendations.length > 0 ? (
                    <div className="rdisc-book-list">
                      {recommendations.map((book, idx) => {
                        const title = book.bookTitle || book.title || 'Untitled Book';
                        const recCount = book.distinctRecommenders ?? book.recommendedByCount ?? 1;
                        return (
                          <div className="rdisc-book-row" key={book.id || idx}>
                            <span className="rdisc-book-icon" aria-hidden="true">
                              📖
                            </span>
                            <img
                              className="rdisc-book-cover"
                              src={book.coverImageUrl || book.coverUrl || PLACEHOLDER}
                              alt={'Cover of ' + title}
                              onError={(e) => {
                                e.currentTarget.onerror = null;
                                e.currentTarget.src = PLACEHOLDER;
                              }}
                            />
                            <div className="rdisc-book-info">
                              <b className="rdisc-book-title">{title}</b>
                              <span className="rdisc-book-author">by {book.author || 'Unknown'}</span>
                              <span className="rdisc-book-metric">
                                ⭐ Recommended by {recCount} reader{recCount === 1 ? '' : 's'}
                              </span>
                            </div>
                            <Link to="/reader-recommendations" className="rdisc-mini-btn" title="See reviews">
                              Reviews →
                            </Link>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="rdisc-empty-sub">No recommendations yet.</p>
                  )}
                </div>

                {/* Top Recommenders Leaderboard */}
                {topRecommenders.length > 0 && (
                  <div className="rdisc-card">
                    <div className="rdisc-card-header">
                      <h3>🏆 Top Community Recommenders</h3>
                    </div>
                    <div className="rdisc-leaderboard">
                      {topRecommenders.map((user, idx) => {
                        const medals = ['🥇', '🥈', '🥉'];
                        const count = user.recommendationsCount ?? user.count ?? 0;
                        return (
                          <div className="rdisc-leader-row" key={idx}>
                            <span className="rdisc-leader-rank">{medals[idx] || `#${idx + 1}`}</span>
                            <span className="rdisc-leader-avatar">{initialsOf(user.name)}</span>
                            <div className="rdisc-leader-info">
                              <b>{user.name || 'Member'}</b>
                              <span>
                                {count} book recommendation{count === 1 ? '' : 's'}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </section>
            )}

            {/* SECTION 2: EVENTS & CALENDAR */}
            {(activeCategory === 'all' || activeCategory === 'events') && (
              <section className="rdisc-section" aria-labelledby="sec-events-title">
                <div className="rdisc-section-head">
                  <h2 id="sec-events-title">
                    <span aria-hidden="true">📅</span> Events &amp; Calendar
                  </h2>
                  <Link to="/reader-events" className="rdisc-section-link">
                    All Events →
                  </Link>
                </div>

                {/* Event Calendar Widget */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>📅 Monthly Calendar</h3>
                    <span className="rdisc-badge-pill">Scheduled dates</span>
                  </div>
                  <div className="rdisc-cal-wrap">
                    <MonthCal dates={calDates} />
                  </div>
                  <div className="rdisc-cal-legend">
                    <span className="rdisc-legend-dot" aria-hidden="true" />
                    <span>Highlighted days have scheduled reading circle events.</span>
                  </div>
                </div>

                {/* My Registered Events */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>🎟️ My Registered Events</h3>
                    <Link to="/reader-events/registered" className="rdisc-card-sublink">
                      Manage →
                    </Link>
                  </div>

                  {/* Quick Attendance Stats */}
                  <div className="rdisc-stats-row">
                    <div className="rdisc-stat-chip">
                      <span className="rdisc-stat-num">{eventStats.registered}</span>
                      <span className="rdisc-stat-lbl">Registered</span>
                    </div>
                    <div className="rdisc-stat-chip">
                      <span className="rdisc-stat-num">{eventStats.waitlisted}</span>
                      <span className="rdisc-stat-lbl">Waitlisted</span>
                    </div>
                    <div className="rdisc-stat-chip">
                      <span className="rdisc-stat-num">{eventStats.attended}</span>
                      <span className="rdisc-stat-lbl">Attended</span>
                    </div>
                  </div>

                  {registeredEvents.length > 0 ? (
                    <div className="rdisc-events-list">
                      {registeredEvents.slice(0, 4).map((ev, idx) => (
                        <div className="rdisc-event-card" key={ev.id || ev.eventId || idx}>
                          {ev.coverImageUrl && (
                            <img
                              className="rdisc-event-thumb"
                              src={resolveMedia(ev.coverImageUrl)}
                              alt=""
                              onError={(e) => {
                                e.currentTarget.style.display = 'none';
                              }}
                            />
                          )}
                          <div className="rdisc-event-details">
                            <b className="rdisc-event-name">{ev.title || 'Event'}</b>
                            <span className="rdisc-event-meta">
                              📅 {ev.eventDate || 'Upcoming'} {ev.eventTime ? `· ⏰ ${ev.eventTime}` : ''}
                            </span>
                            <span className="rdisc-event-meta">
                              📍 {ev.venue || ev.location || 'Online'}
                            </span>
                            {ev.circleName && (
                              <span className="rdisc-event-circle">in {ev.circleName}</span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rdisc-empty-box">
                      <p>Nothing registered yet.</p>
                      <Link to="/reader-events" className="rdisc-cta-btn">
                        Browse Events &amp; Reserve Seats
                      </Link>
                    </div>
                  )}
                </div>
              </section>
            )}

            {/* SECTION 3: CIRCLES & MEMBERSHIP */}
            {(activeCategory === 'all' || activeCategory === 'circles') && (
              <section className="rdisc-section" aria-labelledby="sec-circles-title">
                <div className="rdisc-section-head">
                  <h2 id="sec-circles-title">
                    <span aria-hidden="true">👥</span> Circles &amp; Community Roles
                  </h2>
                  <Link to="/reader-circles" className="rdisc-section-link">
                    Explore Circles →
                  </Link>
                </div>

                {/* Membership Status Summary */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>📊 Membership Status</h3>
                  </div>
                  <div className="rdisc-stat-cards">
                    <Link to="/reader-circles" className="rdisc-membership-stat">
                      <span className="rdisc-mstat-icon">📚</span>
                      <div>
                        <b>{approvedMemberships.length}</b>
                        <span>Joined Circles</span>
                      </div>
                      <span className="rdisc-chev" aria-hidden="true">›</span>
                    </Link>
                    <Link to="/reader-circles/requests" className="rdisc-membership-stat">
                      <span className="rdisc-mstat-icon">📩</span>
                      <div>
                        <b>{pendingRequestsCount}</b>
                        <span>Pending Requests</span>
                      </div>
                      <span className="rdisc-chev" aria-hidden="true">›</span>
                    </Link>
                    <Link to="/reader-circles/requests" className="rdisc-membership-stat">
                      <span className="rdisc-mstat-icon">ⓘ</span>
                      <div>
                        <b>{declinedRequestsCount}</b>
                        <span>Declined Requests</span>
                      </div>
                      <span className="rdisc-chev" aria-hidden="true">›</span>
                    </Link>
                  </div>
                </div>

                {/* My Circle Roles */}
                <div className="rdisc-card">
                  <div className="rdisc-card-header">
                    <h3>👥 My Circle Roles</h3>
                    <span className="rdisc-badge-pill">Active memberships</span>
                  </div>
                  {approvedMemberships.length > 0 ? (
                    <div className="rdisc-roles-list">
                      {approvedMemberships.map((circle) => {
                        const isOrganizer = !!orgMap[circle.circleId || circle.id];
                        return (
                          <div className="rdisc-role-row" key={circle.id || circle.circleId}>
                            {circle.coverImageUrl ? (
                              <img
                                className="rdisc-circle-avatar"
                                src={resolveMedia(circle.coverImageUrl)}
                                alt=""
                                onError={(e) => {
                                  e.currentTarget.style.display = 'none';
                                }}
                              />
                            ) : (
                              <span className="rdisc-circle-avatar">{initialsOf(circle.name)}</span>
                            )}
                            <div className="rdisc-circle-info">
                              <b className="rdisc-circle-name">{circle.name}</b>
                              <span className="rdisc-circle-members">
                                👥 {circle.memberCount ?? '–'} member{(circle.memberCount ?? 0) === 1 ? '' : 's'}
                              </span>
                            </div>
                            <span className={'rdisc-role-pill ' + (isOrganizer ? 'org' : 'reader')}>
                              {isOrganizer ? 'ORGANIZER' : 'READER'}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <div className="rdisc-empty-box">
                      <p>You haven&rsquo;t joined any circles yet.</p>
                      <Link to="/reader-circles/explore" className="rdisc-cta-btn">
                        Explore &amp; Join Circles
                      </Link>
                    </div>
                  )}
                </div>
              </section>
            )}
          </div>
        )}
      </div>
    </ReaderLayout>
  );
}
