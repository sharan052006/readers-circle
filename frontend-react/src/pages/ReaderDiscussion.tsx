import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { ApiError, api, initialsOf } from '../lib/api';
import './ReaderDiscussion.css';

type DComment = {
  id: string;
  author?: string;
  avatar?: string | null;
  content?: string;
  createdAt?: string;
  parentId?: string | null;
  replyToAuthor?: string | null;
  likesCount?: number;
  userLiked?: boolean;
  replies?: DComment[];
};

type DThread = {
  id: string;
  title?: string;
  author?: string;
  authorAvatar?: string | null;
  timeAgo?: string;
  content?: string;
  repliesCount?: number;
  likesCount?: number;
  userLiked?: boolean;
  comments?: DComment[];
};

type Detail = {
  id: string;
  bookTitle?: string;
  author?: string;
  genre?: string;
  pageCount?: number | null;
  publicationYear?: number | null;
  coverImageUrl?: string;
  keyQuote?: string | null;
  quoteAuthor?: string | null;
  discussionTitle?: string;
  discussionExcerpt?: string;
  tag?: string;
  tags?: string[];
  repliesCount?: number;
  viewsCount?: number;
  timeAgo?: string;
  createdAt?: string | null;
  createdBy?: string;
  createdById?: string;
  membersCount?: number;
  circleId?: string | null;
  circleName?: string | null;
  threads?: DThread[];
  likesCount?: number;
  userLiked?: boolean;
};

import { DISCUSSION_COVER_PLACEHOLDER as FALLBACK } from '../lib/placeholders';

function coverError(e: React.SyntheticEvent<HTMLImageElement>) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = FALLBACK;
}

const AVATAR_COLORS = [
  'linear-gradient(135deg, #7C3AED 0%, #5B21B6 100%)',
  'linear-gradient(135deg, #DB2777 0%, #9D174D 100%)',
  'linear-gradient(135deg, #059669 0%, #065F46 100%)',
  'linear-gradient(135deg, #2563EB 0%, #1E40AF 100%)',
  'linear-gradient(135deg, #D97706 0%, #92400E 100%)',
  'linear-gradient(135deg, #EA580C 0%, #9A3412 100%)',
  'linear-gradient(135deg, #0891B2 0%, #155E75 100%)',
];

function avatarBackground(name?: string | null): string {
  const s = String(name || '?');
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

function ReplyIcon() {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 14l-5-4 5-4" />
      <path d="M4 10h10a6 6 0 0 1 6 6v2" />
    </svg>
  );
}

function HeartIcon({ filled }: { filled?: boolean }) {
  return filled ? (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="#DC2626" stroke="#DC2626" strokeWidth="1" aria-hidden="true">
      <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
    </svg>
  );
}

function KebabIcon() {
  return (
    <svg viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
      <circle cx="10" cy="4" r="1.8" />
      <circle cx="10" cy="10" r="1.8" />
      <circle cx="10" cy="16" r="1.8" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <line x1="22" y1="2" x2="11" y2="13" />
      <polygon points="22 2 15 22 11 13 2 9 22 2" />
    </svg>
  );
}

function BookOpenIcon() {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
      <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
    </svg>
  );
}

function PageIcon() {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  );
}

function QuoteIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" opacity="0.25" aria-hidden="true">
      <path d="M4.583 17.321C3.553 16.227 3 15 3 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 01-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179zm10 0C13.553 16.227 13 15 13 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 01-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179z" />
    </svg>
  );
}

function ChatBubbleIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  );
}

function UsersIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function PhotoIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="18" height="18" rx="3" ry="3" />
      <circle cx="8.5" cy="8.5" r="1.5" />
      <polyline points="21 15 16 10 5 21" />
    </svg>
  );
}

function SmileIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <path d="M8 14s1.5 2 4 2 4-2 4-2" />
      <line x1="9" y1="9" x2="9.01" y2="9" strokeWidth="3" />
      <line x1="15" y1="9" x2="15.01" y2="9" strokeWidth="3" />
    </svg>
  );
}

function countDescendants(c: DComment): number {
  const kids = c.replies ?? [];
  return kids.length + kids.reduce((n, k) => n + countDescendants(k), 0);
}

function countThreadDescendants(t: DThread): number {
  const top = t.comments ?? [];
  return top.length + top.reduce((n, c) => n + countDescendants(c), 0);
}

function countAll(threads: DThread[] | undefined): number {
  if (!threads) return 0;
  return threads.reduce((n, t) => n + 1 + countThreadDescendants(t), 0);
}

function expandLabel(n: number, open: boolean): string {
  const one = n === 1;
  if (open) return one ? 'Hide reply' : `Hide ${n} replies`;
  return one ? 'View 1 reply' : `View ${n} more replies`;
}

function tagsOfDetail(d: Detail | null): string[] {
  if (!d) return [];
  if (d.tags && d.tags.length) return d.tags.slice(0, 4);
  if (d.tag) return [d.tag];
  return [];
}

export default function ReaderDiscussion() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<Detail | null>(null);
  const [err, setErr] = useState('');
  const [sort, setSort] = useState<'oldest' | 'newest'>('newest');
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  type ReplyTarget = { threadId: string; parentId: string | null; name: string };
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null);
  const [bottomText, setBottomText] = useState('');
  const [photoHint, setPhotoHint] = useState('');
  const [busy, setBusy] = useState(false);
  const [sync, setSync] = useState('Connecting...');
  const [meInitial, setMeInitial] = useState('R');
  const threadsRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setErr('');
      const d = await api<Detail>('/api/discussions/' + id);
      setDetail(d);
      setSync('Synced with admin just now');
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        navigate('/auth');
        return;
      }
      setErr(e instanceof Error ? e.message : String(e));
      setSync('Offline');
    }
  }, [id, navigate]);

  useEffect(() => {
    setSync('Syncing with admin data...');
    void load();
    api<{ name?: string; email?: string }>('/api/users/me')
      .then((me) => setMeInitial(initialsOf(me.name || me.email)))
      .catch(() => undefined);
  }, [load]);

  const threads = useMemo(() => {
    const list = detail?.threads ?? [];
    return sort === 'newest' ? [...list].reverse() : list;
  }, [detail, sort]);

  const totalComments = useMemo(() => countAll(detail?.threads), [detail]);

  function toggleExpand(key: string) {
    setExpanded((p) => ({ ...p, [key]: !p[key] }));
  }

  async function refresh() {
    if (!id) return;
    try {
      const d = await api<Detail>('/api/discussions/' + id);
      setDetail(d);
    } catch {
      /* keep current view on refresh failure */
    }
  }

  async function likeDiscussion() {
    if (!id) return;
    try {
      const r = await api<{ liked: boolean; likesCount: number }>('/api/discussions/' + id + '/like', {
        method: 'POST',
      });
      setDetail((d) => (d ? { ...d, likesCount: r.likesCount, userLiked: r.liked } : d));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) navigate('/auth');
    }
  }

  async function likeThread(tid: string) {    try {
      await api('/api/discussions/threads/' + tid + '/like', { method: 'POST' });
      await refresh();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) navigate('/auth');
    }
  }

  async function likeComment(cid: string) {
    try {
      await api('/api/discussions/comments/' + cid + '/like', { method: 'POST' });
      await refresh();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) navigate('/auth');
    }
  }

  function openReply(threadId: string, parentId: string | null, authorName: string) {
    if (replyTarget && replyTarget.threadId === threadId && replyTarget.parentId === parentId) {
      setReplyTarget(null);
      return;
    }
    setReplyTarget({ threadId, parentId, name: authorName });
    window.setTimeout(() => {
      const el = document.getElementById('readerDiscBottomText');
      if (el) {
        el.focus();
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 50);
  }

  async function postReplyToTarget(threadId: string, parentId: string | null) {
    const text = bottomText.trim();
    if (!text || !detail) return;
    setBusy(true);
    try {
      const body: Record<string, string> = { content: text };
      if (parentId) body.parentId = parentId;
      await api('/api/discussions/threads/' + threadId + '/comments', {
        method: 'POST',
        body: JSON.stringify(body),
      });
      setBottomText('');
      setReplyTarget(null);
      if (parentId) setExpanded((p) => ({ ...p, [parentId]: true }));
      else setExpanded((p) => ({ ...p, [threadId]: true }));
      await refresh();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  async function submitBottomOrReply() {
    if (replyTarget) {
      await postReplyToTarget(replyTarget.threadId, replyTarget.parentId);
    } else {
      await postBottom();
    }
  }

  async function postBottom() {
    const text = bottomText.trim();
    if (!text || !detail) return;
    setBusy(true);
    try {
      await api('/api/discussions/' + detail.id + '/threads', {
        method: 'POST',
        body: JSON.stringify({ content: text, title: 'Comment' }),
      });
      setBottomText('');
      await refresh();
      setTimeout(() => {
        if (sort === 'newest') {
          threadsRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
        } else {
          threadsRef.current?.scrollTo({ top: threadsRef.current?.scrollHeight || 0, behavior: 'smooth' });
        }
      }, 60);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  function onPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length) {
      setPhotoHint('Photos cannot be attached to comments yet.');
      window.setTimeout(() => setPhotoHint(''), 3500);
    }
    e.target.value = '';
  }

  function expander(n: number, k: string) {
    if (n <= 0) return null;
    const open = !!expanded[k];
    return (
      <button
        className={'exppill' + (open ? ' is-open' : '')}
        onClick={() => toggleExpand(k)}
        aria-expanded={open}
      >
        <svg
          className={'exp-chevron' + (open ? ' open' : '')}
          viewBox="0 0 20 20"
          fill="currentColor"
          width="13"
          height="13"
          aria-hidden="true"
        >
          <path
            fillRule="evenodd"
            d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
            clipRule="evenodd"
          />
        </svg>
        <span>{expandLabel(n, open)}</span>
      </button>
    );
  }

  function replyBox(_threadId: string, _parentId: string | null, _name: string) {
    return null;
  }

  function renderKids(list: DComment[] | undefined, threadId: string): React.ReactNode {
    if (!list || !list.length) return null;
    return (
      <div className="kidbox">
        {list.map((c) => {
          const n = countDescendants(c);
          const open = !!expanded[c.id];
          return (
            <div className="cmt sub" key={c.id}>
              <span className="face sm" style={{ background: avatarBackground(c.author) }}>
                {initialsOf(c.author)}
              </span>
              <div className="rbody">
                <div className="rhead">
                  <b>{c.author || 'Member'}</b>
                  <span className="dot-sep">·</span>
                  <span className="timestamp">{c.createdAt || ''}</span>
                  <button className="kebab-btn" aria-label="Comment options">
                    <KebabIcon />
                  </button>
                </div>
                <p>{c.content || ''}</p>
                <div className="rfoot">
                  <button
                    className={'mini-like' + (c.userLiked ? ' on' : '')}
                    onClick={() => void likeComment(c.id)}
                    aria-label="Like this reply"
                  >
                    <HeartIcon filled={c.userLiked} />
                    <span>{c.likesCount ?? 0}</span>
                  </button>
                  <button
                    className="mini-reply"
                    onClick={() => openReply(threadId, c.id, c.author || 'Member')}
                    aria-label={'Reply to ' + (c.author || 'Member')}
                  >
                    <ReplyIcon />
                    <span>Reply</span>
                  </button>
                  {n > 0 && expander(n, c.id)}
                  <button className="kebab-btn" aria-label="More actions">
                    <KebabIcon />
                  </button>
                </div>
                {replyBox(threadId, c.id, c.author || 'Member')}
                {open && renderKids(c.replies, threadId)}
              </div>
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <ReaderLayout active="dashboard" syncNote={sync}>
      <div className="rdet-wrap">
        {err ? (
          <div className="err">{err}</div>
        ) : !detail ? (
          <div className="notice">Loading discussion details...</div>
        ) : (
          <>
            <Link className="backlink" to="/reader">
              <svg viewBox="0 0 20 20" width="16" height="16" fill="currentColor" aria-hidden="true">
                <path fillRule="evenodd" d="M17 10a.75.75 0 01-.75.75H5.612l4.158 3.96a.75.75 0 11-1.04 1.08l-5.5-5.25a.75.75 0 010-1.08l5.5-5.25a.75.75 0 111.04 1.08L5.612 9.25H16.25A.75.75 0 0117 10z" clipRule="evenodd" />
              </svg>
              <span>Back to Discussions</span>
            </Link>

            <div className="rdet-grid">
              <main className="rdet-main">
                {/* Hero section */}
                <section className="hero">
                  <div className="hero-cover-container">
                    <img
                      className="hero-cover"
                      src={detail.coverImageUrl || FALLBACK}
                      alt={'Cover of ' + (detail.bookTitle || 'book')}
                      onError={coverError}
                    />
                    <div className="cover-spine-edge" aria-hidden="true" />
                  </div>
                  <div className="hero-body">
                    <span className="hero-eyebrow">Book Discussion</span>
                    <h1>{detail.bookTitle || detail.discussionTitle || 'Discussion'}</h1>
                    <p className="hero-author">
                      by <span className="author-name">{detail.author || 'Unknown Author'}</span>
                    </p>
                    <div className="hero-meta">
                      {detail.genre && (
                        <span className="hero-badge">
                          <BookOpenIcon />
                          <span>{detail.genre}</span>
                        </span>
                      )}
                      {detail.pageCount != null && (
                        <span className="hero-badge">
                          <PageIcon />
                          <span>{detail.pageCount} pages</span>
                        </span>
                      )}
                      {detail.publicationYear != null && (
                        <span className="hero-badge">
                          <CalendarIcon />
                          <span>Published: {detail.publicationYear}</span>
                        </span>
                      )}
                    </div>
                    {/* The discussion post itself — same data as the dashboard card */}
                    {(detail.discussionTitle || detail.discussionExcerpt) && (
                      <div className="post-block">
                        <div className="post-byline">
                          <span className="face xs" style={{ background: avatarBackground(detail.createdBy) }}>
                            {initialsOf(detail.createdBy)}
                          </span>
                          <span className="post-byline-tx">
                            <b>{detail.createdBy || 'Member'}</b>
                            <span className="dot-sep">·</span>
                            <span>in {detail.circleName || 'Reading Circle'}</span>
                            {detail.timeAgo && (
                              <>
                                <span className="dot-sep">·</span>
                                <span>{detail.timeAgo}</span>
                              </>
                            )}
                          </span>
                        </div>
                        {detail.discussionTitle && <h2 className="post-title">{detail.discussionTitle}</h2>}
                        {detail.discussionExcerpt && <p className="post-excerpt">{detail.discussionExcerpt}</p>}
                        {tagsOfDetail(detail).length > 0 && (
                          <div className="post-tags">
                            {tagsOfDetail(detail).map((t, i) => (
                              <span key={i}>#{String(t).replace(/^#+/, '').replace(/\s+/g, '')}</span>
                            ))}
                          </div>
                        )}
                        <div className="post-actions">
                          <button
                            className={'mini-like' + (detail.userLiked ? ' on' : '')}
                            onClick={() => void likeDiscussion()}
                            aria-label="Like this discussion"
                          >
                            <HeartIcon filled={detail.userLiked} />
                            <span>{detail.likesCount ?? 0}</span>
                          </button>
                          <span className="post-count">💬 {detail.repliesCount ?? totalComments} comments</span>
                          {detail.viewsCount != null && (
                            <span className="post-count">👁 {detail.viewsCount} views</span>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                  <button className="kebab-circle" aria-label="Discussion options">
                    <KebabIcon />
                  </button>
                </section>

                {/* Comments section */}
                <section className="clist-card">
                  <div className="clist-head">
                    <div className="clist-title-wrap">
                      <h2>Comments</h2>
                      <span className="count-pill">{totalComments}</span>
                    </div>
                    <div className="sortwrap">
                      <label htmlFor="comment-sort" className="sort-label">Sort:</label>
                      <div className="select-container">
                        <select
                          id="comment-sort"
                          value={sort}
                          onChange={(e) => setSort(e.target.value as 'oldest' | 'newest')}
                          aria-label="Sort comments"
                        >
                          <option value="newest">Newest First</option>
                          <option value="oldest">Oldest First</option>
                        </select>
                        <svg className="select-arrow" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                          <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clipRule="evenodd" />
                        </svg>
                      </div>
                    </div>
                  </div>

                  {threads.length === 0 ? (
                    <div className="empty-notice">
                      <div className="empty-icon">💬</div>
                      <p>No comments yet — be the first to start the discussion below.</p>
                    </div>
                  ) : (
                    <div className="threads-container" ref={threadsRef}>
                      {threads.map((t) => {
                        const n = countThreadDescendants(t);
                        const open = !!expanded[t.id];
                        return (
                          <div className="thread-item" key={t.id}>
                            <div className="cmt">
                              <span className="face" style={{ background: avatarBackground(t.author) }}>
                                {initialsOf(t.author)}
                              </span>
                              <div className="rbody">
                                <div className="rhead">
                                  <b>{t.author || 'Member'}</b>
                                  <span className="dot-sep">·</span>
                                  <span className="timestamp">{t.timeAgo || ''}</span>
                                  <button className="kebab-btn" aria-label="Comment options">
                                    <KebabIcon />
                                  </button>
                                </div>
                                <p>{t.content || ''}</p>
                                <div className="rfoot">
                                  <button
                                    className={'mini-like' + (t.userLiked ? ' on' : '')}
                                    onClick={() => void likeThread(t.id)}
                                    aria-label="Like this comment"
                                  >
                                    <HeartIcon filled={t.userLiked} />
                                    <span>{t.likesCount ?? 0}</span>
                                  </button>
                                  <button
                                    className="mini-reply"
                                    onClick={() => openReply(t.id, null, t.author || 'Member')}
                                    aria-label={'Reply to ' + (t.author || 'Member')}
                                  >
                                    <ReplyIcon />
                                    <span>Reply</span>
                                  </button>
                                  {n > 0 && expander(n, t.id)}
                                  <button className="kebab-btn" aria-label="More actions">
                                    <KebabIcon />
                                  </button>
                                </div>
                                {replyBox(t.id, null, t.author || 'Member')}
                              </div>
                            </div>
                            {open && renderKids(t.comments, t.id)}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>

                {/* Bottom Composer */}
                <section className="composer" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 6 }}>
                  {replyTarget && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '4px 12px',
                        background: '#FAF3EB',
                        borderRadius: 10,
                        fontSize: 12.5,
                        color: 'var(--burgundy)',
                        border: '1px solid #EAD8C7',
                      }}
                    >
                      <span>
                        ↩ Replying to <b>@{replyTarget.name || 'Member'}</b>
                      </span>
                      <button
                        type="button"
                        aria-label="Cancel reply"
                        style={{
                          background: 'none',
                          border: 0,
                          color: 'var(--faint)',
                          cursor: 'pointer',
                          fontWeight: 600,
                          fontSize: 12,
                        }}
                        onClick={() => setReplyTarget(null)}
                      >
                        ✕ Cancel
                      </button>
                    </div>
                  )}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%' }}>
                    <span className="composer-face" title="Your avatar">{meInitial}</span>
                    <div className="cwrap">
                      <input
                        id="readerDiscBottomText"
                        value={bottomText}
                        onChange={(e) => setBottomText(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' && !e.shiftKey && bottomText.trim()) {
                            e.preventDefault();
                            void submitBottomOrReply();
                          } else if (e.key === 'Escape' && replyTarget) {
                            setReplyTarget(null);
                          }
                        }}
                        placeholder={replyTarget ? `Write a reply to @${replyTarget.name || 'Member'}...` : 'Write a comment...'}
                        aria-label={replyTarget ? 'Write a reply' : 'Write a comment'}
                      />
                      <div className="cicons">
                        <label className="iconbtn" title="Attach a photo" aria-label="Attach a photo">
                          <PhotoIcon />
                          <input type="file" accept="image/*" hidden onChange={onPhoto} />
                        </label>
                        <button
                          type="button"
                          className="iconbtn"
                          title="Insert emoji"
                          aria-label="Insert emoji"
                          onClick={() => setBottomText((v) => v + ' 😊')}
                        >
                          <SmileIcon />
                        </button>
                      </div>
                    </div>
                    <button
                      className="postbtn"
                      disabled={busy || !bottomText.trim()}
                      onClick={() => void submitBottomOrReply()}
                      aria-label={replyTarget ? 'Post reply' : 'Post comment'}
                    >
                      <span>{replyTarget ? 'Post Reply' : 'Post Comment'}</span>
                      <SendIcon />
                    </button>
                  </div>
                  {photoHint && <span className="photohint">{photoHint}</span>}
                </section>
              </main>

              {/* Right rail sidebar */}
              <aside className="rdet-rail" aria-label="Book and community info">
                <section className="panel book-panel">
                  <div className="panel-header">
                    <h3>Book Details</h3>
                  </div>
                  <div className="bookrow">
                    <div className="bookrow-thumb">
                      <img
                        src={detail.coverImageUrl || FALLBACK}
                        alt={'Cover of ' + (detail.bookTitle || 'book')}
                        onError={coverError}
                      />
                    </div>
                    <div className="bookrow-info">
                      <b>{detail.bookTitle || 'Book Discussion'}</b>
                      <span>by {detail.author || 'Unknown'}</span>
                    </div>
                  </div>
                  <div className="book-specs">
                    <div className="spec-row">
                      <span className="spec-label">
                        <BookOpenIcon /> Genre
                      </span>
                      <span className="spec-val">{detail.genre || '–'}</span>
                    </div>
                    <div className="spec-row">
                      <span className="spec-label">
                        <PageIcon /> Pages
                      </span>
                      <span className="spec-val">{detail.pageCount == null ? '–' : `${detail.pageCount} pages`}</span>
                    </div>
                    <div className="spec-row">
                      <span className="spec-label">
                        <CalendarIcon /> Published
                      </span>
                      <span className="spec-val">{detail.publicationYear == null ? '–' : detail.publicationYear}</span>
                    </div>
                  </div>
                  {detail.keyQuote && (
                    <div className="quotebox">
                      <QuoteIcon />
                      <div className="quote-content">
                        <p className="quote-text">“{detail.keyQuote}”</p>
                        <p className="quote-by">— {detail.quoteAuthor || detail.author || 'Author'}</p>
                      </div>
                    </div>
                  )}
                </section>

                <section className="panel stats-panel">
                  <div className="panel-header">
                    <h3>Community Stats</h3>
                  </div>
                  <div className="stats-metric-grid">
                    <div className="metric-box">
                      <div className="metric-top">
                        <ChatBubbleIcon />
                        <span className="metric-label">Discussions</span>
                      </div>
                      <div className="metric-value">{(detail.threads || []).length}</div>
                    </div>
                    <div className="metric-box">
                      <div className="metric-top">
                        <UsersIcon />
                        <span className="metric-label">Members</span>
                      </div>
                      <div className="metric-value">{detail.membersCount ?? 0}</div>
                    </div>
                  </div>
                  <div className="stats-caption">
                    <span className="live-dot" />
                    <span>People who joined this discussion</span>
                  </div>
                </section>
              </aside>
            </div>
          </>
        )}
      </div>
    </ReaderLayout>
  );
}
