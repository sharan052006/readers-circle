import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { ApiError, api, apiBase, getAccess, initialsOf, type Me } from '../lib/api';
import './ReaderDashboard.css';

type FeedItem = {
  id: string | number;
  createdBy?: string;
  createdById?: string;
  circleId?: string;
  circleName?: string;
  timeAgo?: string;
  discussionTitle?: string;
  bookTitle?: string;
  author?: string;
  genre?: string;
  discussionExcerpt?: string;
  tags?: string[];
  tag?: string;
  coverImageUrl?: string;
  likesCount?: number;
  repliesCount?: number;
  userLiked?: boolean;
};

type TrendBook = {
  bookTitle?: string;
  author?: string;
  discussionCount?: number;
  discussions?: number;
  coverImageUrl?: string;
};

type RecBook = {
  bookTitle?: string;
  title?: string;
  author?: string;
  coverImageUrl?: string;
  coverUrl?: string;
  recommendedByCount?: number;
  distinctRecommenders?: number;
  upvotesCount?: number;
};

type RegEvent = {
  title?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  circleName?: string;
  coverImageUrl?: string;
};

type DashboardResp = {
  feed?: FeedItem[];
  discussions?: FeedItem[];
  trendingBooks?: TrendBook[];
  recommendedBooks?: RecBook[];
  registeredEvents?: RegEvent[];
};

type LikeResp = {
  liked: boolean;
  likesCount: number;
};

import { DISCUSSION_COVER_PLACEHOLDER as PLACEHOLDER } from '../lib/placeholders';

function is401(e: unknown): boolean {
  return e instanceof ApiError && e.status === 401;
}

function tagsOf(d: FeedItem): string[] {
  if (d.tags && d.tags.length) return d.tags.slice(0, 4);
  if (d.tag) return [d.tag];
  return [];
}

function genrePillsOf(d: FeedItem): string[] {
  const out: string[] = [];
  for (const t of [d.tag, d.genre]) {
    const v = (t || '').trim();
    if (v && out.every((x) => x.toLowerCase() !== v.toLowerCase())) out.push(v);
  }
  return out.slice(0, 3);
}

function hashLabel(t: string): string {
  const v = String(t || '')
    .trim()
    .replace(/^#+/, '')
    .replace(/\s+/g, '');
  return v ? '#' + v : '';
}

function coverError(e: React.SyntheticEvent<HTMLImageElement>) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = PLACEHOLDER;
}

export default function ReaderDashboard({ isOrganizer = false }: { isOrganizer?: boolean } = {}) {
  const navigate = useNavigate();
  const [feed, setFeed] = useState<FeedItem[]>([]);
  const [loadingFeed, setLoadingFeed] = useState(true);
  const [feedError, setFeedError] = useState('');
  const [trending, setTrending] = useState<TrendBook[]>([]);
  const [sync, setSync] = useState('Connecting...');
  const [liking, setLiking] = useState<Record<string, boolean>>({});
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [copyToast, setCopyToast] = useState('');
  const activeMenuRef = useRef<HTMLDivElement | null>(null);
  const copyToastTimer = useRef<number | undefined>(undefined);

  const [showModal, setShowModal] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createErr, setCreateErr] = useState('');
  const [circles, setCircles] = useState<{ id: string; name: string }[]>([]);
  const [formBookTitle, setFormBookTitle] = useState('');
  const [formAuthor, setFormAuthor] = useState('');
  const [formDiscTitle, setFormDiscTitle] = useState('');
  const [formGenre, setFormGenre] = useState('Fiction');
  const [formExcerpt, setFormExcerpt] = useState('');
  const [formCoverUrl, setFormCoverUrl] = useState('');
  const [formCircleId, setFormCircleId] = useState('');

  useEffect(() => {
    return () => {
      window.clearTimeout(copyToastTimer.current);
    };
  }, []);

  useEffect(() => {
    if (!openMenuId) return;

    function handlePointerDown(e: MouseEvent | TouchEvent) {
      if (activeMenuRef.current && !activeMenuRef.current.contains(e.target as Node)) {
        setOpenMenuId(null);
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpenMenuId(null);
      }
    }

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [openMenuId]);

  async function loadAll() {
    setSync('Syncing with admin data...');
    setLoadingFeed(true);
    setFeedError('');
    try {
      const dash = await api<DashboardResp>('/api/dashboard?feedLimit=10');
      const feedItems = dash.feed ?? dash.discussions ?? [];
      setFeed(Array.isArray(feedItems) ? feedItems : []);
      setTrending(Array.isArray(dash.trendingBooks) ? dash.trendingBooks : []);
      try {
        const cs = await api<{ id: string; name: string }[]>('/api/circles');
        setCircles(Array.isArray(cs) ? cs : []);
      } catch {
        /* optional */
      }
      setSync('Synced with admin just now');
    } catch (e) {
      if (is401(e)) {
        navigate('/auth');
        return;
      }
      try {
        const f = await api<FeedItem[] | DashboardResp>('/api/discussions');
        const feedItems = Array.isArray(f) ? f : (f.feed ?? f.discussions ?? []);
        setFeed(Array.isArray(feedItems) ? feedItems : []);
        try {
          const tb = await api<TrendBook[]>('/api/books/trending?limit=3');
          setTrending(Array.isArray(tb) ? tb : []);
        } catch {
          /* rail keeps previous value */
        }

        setSync('Synced with admin just now');
      } catch {
        setFeed([]);
        setFeedError(
          'Could not reach the backend at ' + apiBase() + '. Please check backend connectivity, then reload.',
        );
        setSync('Offline');
      }
    } finally {
      setLoadingFeed(false);
    }
  }

  useEffect(() => {
    if (!getAccess()) {
      navigate('/auth');
      return;
    }
    // Role-transition notices (promotion congratulations / demotion notice)
    // live in ReaderLayout, which wraps every reader screen. Here we only
    // keep admins out of the reader dashboard.
    let on = true;
    async function guard() {
      try {
        const me = await api<Me>('/api/users/me');
        if (!on) return;
        if (me.role === 'ADMIN') {
          navigate('/admin');
          return;
        }
        void loadAll();
      } catch (e) {
        if (!on) return;
        if (is401(e)) navigate('/auth');
        else void loadAll();
      }
    }
    void guard();
    return () => {
      on = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  async function toggleLike(id: string | number) {
    const key = String(id);
    setLiking((p) => ({ ...p, [key]: true }));
    try {
      const r = await api<LikeResp>('/api/discussions/' + key + '/like', { method: 'POST' });
      setFeed((prev) =>
        prev.map((d) =>
          String(d.id) === key ? { ...d, likesCount: r.likesCount, userLiked: r.liked } : d,
        ),
      );
    } catch (e) {
      if (is401(e)) navigate('/auth');
      else window.alert(e instanceof Error ? e.message : String(e));
    } finally {
      setLiking((p) => ({ ...p, [key]: false }));
    }
  }

  async function handleCopyLink(id: string | number) {
    const key = String(id);
    const url = `${window.location.origin}/reader-discussions/${key}`;
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(url);
      } else {
        const ta = document.createElement('textarea');
        ta.value = url;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      setCopyToast('Discussion link copied to clipboard!');
      window.clearTimeout(copyToastTimer.current);
      copyToastTimer.current = window.setTimeout(() => {
        setCopyToast('');
      }, 2500);
    } catch {
      window.prompt('Copy discussion link:', url);
    }
  }


  function onCoverFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) {
      setCreateErr('Cover image must be under 5MB.');
      return;
    }
    const r = new FileReader();
    r.onload = () => {
      if (typeof r.result === 'string') {
        setFormCoverUrl(r.result);
      }
    };
    r.readAsDataURL(f);
  }

  async function handleCreateDiscussion(e: React.FormEvent) {
    e.preventDefault();
    if (!formBookTitle.trim() || !formAuthor.trim()) {
      setCreateErr('Book title and author are required.');
      return;
    }
    setCreating(true);
    setCreateErr('');
    try {
      const payload = {
        bookTitle: formBookTitle.trim(),
        author: formAuthor.trim(),
        discussionTitle: formDiscTitle.trim() || `${formBookTitle.trim()} – Discussion & Analysis`,
        genre: formGenre.trim() || 'General',
        tag: formGenre.trim() || 'General',
        discussionExcerpt: formExcerpt.trim() || `Discussion about ${formBookTitle.trim()} by ${formAuthor.trim()}.`,
        coverImageUrl: formCoverUrl.trim() || null,
        circleId: formCircleId || null,
      };
      const created = await api<{ id: string }>('/api/discussions', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      setShowModal(false);
      setFormBookTitle('');
      setFormAuthor('');
      setFormDiscTitle('');
      setFormGenre('Fiction');
      setFormExcerpt('');
      setFormCoverUrl('');
      setFormCircleId('');
      await loadAll();
      if (created?.id) {
        navigate('/reader-discussions/' + created.id);
      }
    } catch (err) {
      setCreateErr(err instanceof Error ? err.message : String(err));
    } finally {
      setCreating(false);
    }
  }

  // Right-side widgets for mobile right drawer
  const mobileHighlights = (
    <div className="rmobile-dash-highlights">
      {/* Trending Books Widget */}
      <div className="rmobile-highlight-section">
        <div className="rmobile-hl-section-head">
          <h4>🔥 Trending Books</h4>
        </div>
        {(!trending || trending.length === 0) ? (
          <div className="rmobile-hl-empty">No trending books yet.</div>
        ) : (
          (trending || []).slice(0, 3).map((b, i) => (
            <div className="rmobile-hl-card" key={i}>
              <span className="rmobile-hl-rank">{i + 1}</span>
              {b.coverImageUrl && (
                <img
                  src={b.coverImageUrl}
                  alt=""
                  className="rmobile-hl-thumb"
                  onError={coverError}
                />
              )}
              <div className="rmobile-hl-info">
                <b>{b.bookTitle || 'Trending Title'}</b>
                <span>{b.author || ''}</span>
                <small>{b.discussionCount ?? b.discussions ?? 0} discussions</small>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );

  return (
    <ReaderLayout active="dashboard" syncNote={sync} highlights={mobileHighlights}>
      <div className="content">
        <main className="feed" aria-live="polite">
          {isOrganizer && (
            <div className="org-mode-banner" role="status" aria-label="Organiser Mode">
              <div className="org-mode-content">
                <b className="org-mode-title">👑 Organiser Mode</b>
                <span className="org-mode-desc">
                  You have full reader access plus organiser tools to manage your reading circles and events directly from the Circles and Events tabs.
                </span>
              </div>
            </div>
          )}
          <div className="feed-header">
            <div className="feed-header-left">
              <h1>Discussions</h1>
              <p>Explore discussions and share your thoughts with fellow readers.</p>
            </div>
            <button className="start-disc-btn" onClick={() => setShowModal(true)}>
              ➕ Start a Discussion
            </button>
          </div>
          {loadingFeed ? (
            <div className="notice">Loading your reading home...</div>
          ) : feedError ? (
            <div className="err">{feedError}</div>
          ) : feed.length === 0 ? (
            <div className="notice">
              <b>No discussions yet.</b>
              <br />
              Be the first to start a conversation, or explore reading circles to join ongoing book discussions.
            </div>
          ) : (
            feed.map((d) => {
              const key = String(d.id);
              const title = d.discussionTitle || d.bookTitle || 'Discussion';
              return (
                <article className="dcard" key={key}>
                  <img
                    className="dcover"
                    src={d.coverImageUrl || PLACEHOLDER}
                    alt={'Cover of ' + (d.bookTitle || 'book')}
                    onError={coverError}
                  />
                  <div className="dbody">
                  <div className="byline">
                    <span className="face">{initialsOf(d.createdBy)}</span>
                    <div className="who">
                      <b>{d.createdBy || 'Member'}</b>
                      <span>
                        in {d.circleName || 'Reading Circle'} &nbsp;·&nbsp; {d.timeAgo || ''}
                      </span>
                    </div>
                    <span className="badge">💬 Discussion</span>
                    <div
                      className="kebab-wrap"
                      ref={openMenuId === key ? activeMenuRef : undefined}
                    >
                      <button
                        type="button"
                        className="kebab"
                        aria-label="More actions"
                        aria-haspopup="true"
                        aria-expanded={openMenuId === key}
                        onClick={(e) => {
                          e.stopPropagation();
                          e.preventDefault();
                          setOpenMenuId((cur) => (cur === key ? null : key));
                        }}
                      >
                        ···
                      </button>
                      {openMenuId === key && (
                        <div
                          className="kebab-menu"
                          role="menu"
                          aria-label="Discussion actions"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <button
                            type="button"
                            role="menuitem"
                            className="kebab-item"
                            onClick={(e) => {
                              e.stopPropagation();
                              setOpenMenuId(null);
                              navigate('/reader-discussions/' + key);
                            }}
                          >
                            <span className="kebab-item-icon" aria-hidden="true">💬</span>
                            <span>View discussion</span>
                          </button>
                          <button
                            type="button"
                            role="menuitem"
                            className="kebab-item"
                            onClick={(e) => {
                              e.stopPropagation();
                              setOpenMenuId(null);
                              void handleCopyLink(key);
                            }}
                          >
                            <span className="kebab-item-icon" aria-hidden="true">🔗</span>
                            <span>Copy link</span>
                          </button>
                          <button
                            type="button"
                            role="menuitem"
                            className="kebab-item"
                            disabled={!!liking[key]}
                            onClick={(e) => {
                              e.stopPropagation();
                              setOpenMenuId(null);
                              void toggleLike(d.id);
                            }}
                          >
                            <span className="kebab-item-icon" aria-hidden="true">♥</span>
                            <span>{d.userLiked ? 'Unlike discussion' : 'Like discussion'}</span>
                          </button>
                          {(d.circleId || d.circleName) && (
                            <button
                              type="button"
                              role="menuitem"
                              className="kebab-item"
                              onClick={(e) => {
                                e.stopPropagation();
                                setOpenMenuId(null);
                                navigate(d.circleId ? '/reader-circles/circle/' + d.circleId : '/reader-circles');
                              }}
                            >
                              <span className="kebab-item-icon" aria-hidden="true">⭕</span>
                              <span>View circle</span>
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="gpills">
                    {genrePillsOf(d).map((t, i) => (
                      <span key={i}>{t}</span>
                    ))}
                  </div>
                  <div className="dmain">
                      <h2>{title}</h2>
                      {(d.bookTitle || d.author) && (
                        <p className="bookline">
                          📖 {d.bookTitle || 'Unknown Book'}
                          {d.author ? (
                            <>
                              {' '}· by <span className="bookauthor">{d.author}</span>
                            </>
                          ) : null}
                        </p>
                      )}
                      <p className="excerpt">{d.discussionExcerpt || ''}</p>
                      <div className="hpills">
                        {tagsOf(d)
                          .map(hashLabel)
                          .filter(Boolean)
                          .map((t, i) => (
                            <span key={i}>{t}</span>
                          ))}
                      </div>
                      <div className="dfoot">
                        <div className="counts">
                        <button
                          className="like"
                          aria-pressed={!!d.userLiked}
                          disabled={!!liking[key]}
                          onClick={() => void toggleLike(d.id)}
                        >
                          <span className="h">♥</span> <span>{d.likesCount ?? 0}</span>
                        </button>
                        <span>💬 {d.repliesCount ?? 0}</span>
                        </div>
                      <button className="viewbtn" onClick={() => navigate('/reader-discussions/' + key)}>
                        View Discussion <span aria-hidden="true">→</span>
                      </button>
                      </div>
                  </div>
                  </div>
                </article>
              );
            })
          )}
        </main>
        <aside className="railcards" aria-label="Trending books">
          <section className="panel" aria-labelledby="tTrend">
            <h3 id="tTrend">🔥 Trending Books</h3>
            <div className="rail-cards-track">
              {(!trending || trending.length === 0) ? (
                <div className="empty">No trending books yet.</div>
              ) : (
                (trending || []).slice(0, 3).map((b, i) => (
                  <div className="rank" key={i}>
                    <span className="n">{i + 1}</span>
                    {b.coverImageUrl && (
                      <img src={b.coverImageUrl} alt={'Cover of ' + (b.bookTitle || '')} onError={coverError} />
                    )}
                    <div>
                      <b>{b.bookTitle || ''}</b>
                      <span>{b.author || ''}</span>
                      <span>{b.discussionCount ?? b.discussions ?? 0} discussions</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        </aside>
      </div>
      {showModal && (
        <div className="disc-modal-overlay" onClick={() => !creating && setShowModal(false)}>
          <div className="disc-modal" onClick={(e) => e.stopPropagation()}>
            <div className="disc-modal-head">
              <h2>Start a Discussion</h2>
              <button
                className="disc-modal-close"
                onClick={() => !creating && setShowModal(false)}
                aria-label="Close dialog"
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleCreateDiscussion}>
              <div className="disc-modal-body">
                {createErr && <div className="err">{createErr}</div>}
                <div className="disc-form-row">
                  <div className="disc-form-group">
                    <label>Book Title *</label>
                    <input
                      type="text"
                      placeholder="e.g. Dune"
                      value={formBookTitle}
                      onChange={(e) => setFormBookTitle(e.target.value)}
                      required
                    />
                  </div>
                  <div className="disc-form-group">
                    <label>Author *</label>
                    <input
                      type="text"
                      placeholder="e.g. Frank Herbert"
                      value={formAuthor}
                      onChange={(e) => setFormAuthor(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="disc-form-group">
                  <label>Discussion Title (Optional)</label>
                  <input
                    type="text"
                    placeholder="e.g. Dune - A World Beyond Our Own"
                    value={formDiscTitle}
                    onChange={(e) => setFormDiscTitle(e.target.value)}
                  />
                </div>

                <div className="disc-form-row">
                  <div className="disc-form-group">
                    <label>Genre / Category</label>
                    <select value={formGenre} onChange={(e) => setFormGenre(e.target.value)}>
                      <option value="Fiction">Fiction</option>
                      <option value="Non-Fiction">Non-Fiction</option>
                      <option value="Classic">Classic</option>
                      <option value="Science Fiction">Science Fiction</option>
                      <option value="Philosophy">Philosophy</option>
                      <option value="History">History</option>
                      <option value="Tamil Literature">Tamil Literature</option>
                      <option value="General">General</option>
                    </select>
                  </div>
                  <div className="disc-form-group">
                    <label>Circle (Optional)</label>
                    <select value={formCircleId} onChange={(e) => setFormCircleId(e.target.value)}>
                      <option value="">General Community</option>
                      {circles.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="disc-form-group">
                  <label>Initial Thoughts / Excerpt</label>
                  <textarea
                    rows={3}
                    placeholder="What did you think of the book? Share your thoughts to start the conversation..."
                    value={formExcerpt}
                    onChange={(e) => setFormExcerpt(e.target.value)}
                  />
                </div>

                <div className="disc-form-group">
                  <label>Cover Image (Optional)</label>
                  {formCoverUrl ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <img
                        src={formCoverUrl}
                        alt="Cover preview"
                        style={{ width: 64, height: 80, objectFit: 'cover', borderRadius: 8, border: '1px solid #E5DED2' }}
                      />
                      <label className="disc-cancel-btn" style={{ cursor: 'pointer', display: 'inline-block' }}>
                        Change Image
                        <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden onChange={onCoverFile} />
                      </label>
                      <button
                        type="button"
                        className="disc-cancel-btn"
                        style={{ color: '#8F2B22' }}
                        onClick={() => setFormCoverUrl('')}
                      >
                        Remove
                      </button>
                    </div>
                  ) : (
                    <div>
                      <input type="file" accept=".jpg,.jpeg,.png,.webp" onChange={onCoverFile} />
                      <span style={{ fontSize: 12, color: '#8A8175', marginTop: 4, display: 'block' }}>
                        Upload a book or discussion cover image (JPG, PNG, WebP · Max 5MB)
                      </span>
                    </div>
                  )}
                </div>
              </div>
              <div className="disc-modal-foot">
                <button
                  type="button"
                  className="disc-cancel-btn"
                  onClick={() => setShowModal(false)}
                  disabled={creating}
                >
                  Cancel
                </button>
                <button type="submit" className="disc-submit-btn" disabled={creating}>
                  {creating ? 'Publishing…' : 'Publish Discussion'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {copyToast && (
        <div className="kebab-toast" role="status" aria-live="polite">
          <span aria-hidden="true">✓</span>
          <span>{copyToast}</span>
        </div>
      )}
    </ReaderLayout>
  );
}
