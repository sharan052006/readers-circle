import { Fragment, useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  api,
  ApiError,
  apiBase,
  clearTokens,
  fmtDate,
  getAccess,
  initialsOf,
  resolveMedia,
  setApiBase,
  timeAgo,
  type Me,
} from '../../lib/api';
import { COVER_PLACEHOLDER, DISCUSSION_COVER_PLACEHOLDER } from '../../lib/placeholders';
import './AdminApp.css';
import '../ReaderDashboard.css';
import '../ReaderDiscussion.css';
import AdminBooks from './AdminBooks';
import AdminOrders from './AdminOrders';
import RecommendationsView from '../../components/Recommendations';

type AdminView = 'dashboard' | 'chat' | 'discussions' | 'recommend' | 'usershome' | 'analytics' | 'settings' | 'books' | 'orders';

function parseView(v: string | null): AdminView {
  if (v === 'chat' || v === 'discussions' || v === 'recommend' || v === 'usershome' || v === 'analytics' || v === 'settings' || v === 'books' || v === 'orders') return v;
  return 'dashboard';
}

function errMsg(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Request failed';
}

/* Reader-parity helpers (same look as ReaderDashboard / ReaderDiscussion) */
const RD_PLACEHOLDER = DISCUSSION_COVER_PLACEHOLDER;
const RD_FALLBACK = DISCUSSION_COVER_PLACEHOLDER;

function rdCoverError(e: React.SyntheticEvent<HTMLImageElement>, fallback: string) {
  const img = e.currentTarget;
  img.onerror = null;
  img.src = fallback;
}

function rdGenrePills(d: { genre?: string; tag?: string }): string[] {
  const out: string[] = [];
  for (const t of [d.tag, d.genre]) {
    const v = (t || '').trim();
    if (v && out.every((x) => x.toLowerCase() !== v.toLowerCase())) out.push(v);
  }
  return out.slice(0, 3);
}

function rdHashLabel(t: string): string {
  const v = String(t || '')
    .trim()
    .replace(/^#+/, '')
    .replace(/\s+/g, '');
  return v ? '#' + v : '';
}

type DashboardData = {
  stats: {
    totalCircles: number;
    circlesTrend: number;
    circlesChangePct: number;
    totalEvents: number;
    eventsTrend: number;
    eventsChangePct: number;
    totalMembers: number;
    membersTrend: number;
    membersChangePct: number;
    totalDiscussions: number;
    discussionsTrend: number;
    discussionsChangePct: number;
  };
  overview: {
    circlesCreatedLast7Days: { value: number; delta: number };
    newMembersLast7Days: { value: number; delta: number };
    eventSignupsLast7Days: { value: number; delta: number };
    activeDiscussionsLast7Days: { value: number; delta: number };
  };
  recentCircles: Array<{
    id?: string;
    name?: string;
    description?: string;
    coverImageUrl?: string;
    membersCount?: number;
    createdAt?: string;
    organizerName?: string;
    organizerRole?: string;
  }>;
  topCircles: Array<{
    id?: string;
    name?: string;
    category?: string;
    city?: string;
    membersCount?: number;
    discussionsCount?: number;
    eventsCount?: number;
    activity7d?: number;
    membersDelta?: number;
    discussionsDelta?: number;
    eventsDelta?: number;
    status?: string;
    coverImageUrl?: string;
  }>;
  upcomingEvents: Array<{
    id?: string;
    title?: string;
    readingTopic?: string;
    eventDate?: string;
    eventTime?: string;
    dateFormatted?: string;
    circleName?: string;
    circleCoverImageUrl?: string;
    registeredCount?: number;
    capacity?: number;
    venue?: string;
    location?: string;
    mode?: string;
    type?: string;
    status?: string;
  }>;
  circleDistribution: {
    total: number;
    items: Array<{ id?: string; name?: string; membersCount?: number; percent?: number }>;
  };
  recentActivity: Array<{
    type?: string;
    icon?: string;
    description?: string;
    circleName?: string;
    actorName?: string;
    createdAt?: string;
  }>;
  growth: { labels: string[]; members: number[]; events: number[]; circles: number[] };
};

type ChatCircle = {
  id: string;
  name?: string;
  city?: string;
  memberCount?: number;
  hidden?: boolean;
};

type Lounge = { circleId: string; name?: string; memberCount?: number };

type ChatMsg = {
  id: string;
  senderId?: string;
  senderName?: string;
  senderAvatarUrl?: string;
  content?: string;
  sentAt?: string;
  likeCount?: number;
  likedByMe?: boolean;
  deleted?: boolean;
};

type Leader = { id?: string; name?: string; email?: string; role?: string };

type Page<T> = { content: T[]; number: number; size: number; totalElements: number };

type BdComment = { author?: string; content?: string; createdAt?: string };
type BdThread = {
  id: string;
  title?: string;
  author?: string;
  content?: string;
  timeAgo?: string;
  repliesCount?: number;
  likesCount?: number;
  comments?: BdComment[];
};
type Discussion = {
  id: string;
  bookTitle?: string;
  author?: string;
  genre?: string;
  tag?: string;
  tags?: string[];
  circleName?: string;
  discussionTitle?: string;
  discussionExcerpt?: string;
  coverImageUrl?: string;
  createdBy?: string;
  repliesCount?: number;
  viewsCount?: number;
  likesCount?: number;
  userLiked?: boolean;
  timeAgo?: string;
  threads?: BdThread[];
};
type DiscussionDetail = Discussion & {
  pageCount?: number | null;
  publicationYear?: number | null;
  keyQuote?: string | null;
  quoteAuthor?: string | null;
  membersCount?: number;
};

type UserRow = {
  id: string;
  name?: string;
  email?: string;
  role?: string;
  active?: boolean;
  createdAt?: string;
  profilePictureUrl?: string;
  blockedReason?: string;
};
type UhSummary = { totalUsers: number; totalReaders: number; totalOrganizers: number; blockedUsers: number };

type AnaRow = { name?: string; membersCount?: number; eventsCount?: number; activityRate?: number };

const SET_TOGGLES: Array<[string, string, string]> = [
  ['joinRequest', 'New join request', 'Notify when a user requests to join a circle.'],
  ['memberJoined', 'New member joined', 'Notify when a new member is approved and joins a circle.'],
  ['eventCreated', 'New event created', 'Notify when an organizer creates a new event.'],
  ['eventRegistration', 'Event registration', 'Notify when a user registers for an event.'],
  ['bookRecommendation', 'New book recommendation', 'Notify when a member creates a new book recommendation.'],
  ['discussion', 'New discussion', 'Notify when a member starts a new discussion.'],
  ['report', 'New report', 'Notify when a user or content is reported.'],
];

const LOUNGE_RULES = [
  'Dedicated channel for organisers and platform admin to collaborate.',
  'Coordinate circle events, reading themes, and moderation updates.',
  'Keep communication friendly, respectful, and constructive.',
  'Admin provides direct support for organiser concerns and circle management.',
];
const CIRCLE_RULES = [
  'Keep the conversation respectful and friendly.',
  'Avoid spoilers (use spoiler tags if needed).',
  'Stay on topic and relevant to the circle.',
  'No hate speech or personal attacks.',
];

function downloadCsv(name: string, rows: UserRow[]) {
  const csv =
    'Name,Email,Role,Joined,Active\n' +
    rows
      .map((u) =>
        [u.name || '', u.email || '', u.role || '', u.createdAt || '', u.active ? 'Yes' : 'No']
          .map((v) => '"' + String(v).replace(/"/g, '""') + '"')
          .join(','),
      )
      .join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
  a.download = name;
  a.click();
  window.setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

const DASH_PALETTE = ['#7A4526', '#2F6B3A', '#E08A3C', '#C9A27A', '#8E7CC3', '#B4623C', '#4E7A57', '#D9B26A'];

function sparkGeometry(values: number[]) {
  const vals = values.length >= 2 ? values : [0, 0];
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const span = Math.max(1, max - min);
  const w = 120;
  const h = 30;
  const step = w / (vals.length - 1);
  const y = (v: number) => h - 3 - ((v - min) / span) * (h - 8);
  const line = vals.map((v, i) => (i * step).toFixed(1) + ',' + y(v).toFixed(1)).join(' ');
  return { line, area: '0,' + h + ' ' + line + ' ' + w + ',' + h };
}

function activityBars(value: number) {
  return Array.from({ length: 7 }, (_, i) => {
    const f = (Math.sin((i + 1) * 1.6 + value * 0.7) + 1) / 2;
    return Math.round(16 + f * 84 * (value > 0 ? 1 : 0.3));
  });
}

function dayNum(d?: string) {
  if (!d) return '--';
  const dt = new Date(d + 'T00:00:00');
  return isNaN(dt.getTime()) ? '--' : dt.getDate();
}

function monShort(d?: string) {
  if (!d) return '';
  const dt = new Date(d + 'T00:00:00');
  return isNaN(dt.getTime()) ? '' : dt.toLocaleString('en-US', { month: 'short' }).toUpperCase();
}

function regPercent(e: { registeredCount?: number; capacity?: number }) {
  const cap = e.capacity || 0;
  return cap > 0 ? Math.min(100, Math.round(((e.registeredCount || 0) / cap) * 100)) : 0;
}

function statIcon(key: string) {
  if (key === 'circles')
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
        <circle cx="9" cy="8" r="3.2" />
        <path d="M3.4 19a5.6 5.6 0 0 1 11.2 0z" />
        <circle cx="17" cy="9" r="2.6" />
        <path d="M14.6 19a5 5 0 0 1 6.9-4.6l-.8 1.9A3.1 3.1 0 0 0 16.6 19z" />
      </svg>
    );
  if (key === 'events')
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
        <path d="M7 2h2v2h6V2h2v2h2a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2zM5 9v10h14V9z" />
      </svg>
    );
  if (key === 'members')
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
        <circle cx="12" cy="8" r="3.6" />
        <path d="M4.5 20a7.5 7.5 0 0 1 15 0z" />
      </svg>
    );
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
      <path d="M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-5 4V6a2 2 0 0 1 2-2zM7 8h10v1.6H7zm0 3.4h7V13H7z" />
    </svg>
  );
}

export default function AdminApp() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [view, setView] = useState<AdminView>(() => parseView(params.get('view')));
  const [me, setMe] = useState<Me | null>(null);
  const [gateErr, setGateErr] = useState('');
  const [authLoading, setAuthLoading] = useState(true);
  const [baseUrl, setBaseUrl] = useState(apiBase());
  const [pageErr, setPageErr] = useState('');
  const [syncLbl, setSyncLbl] = useState('Real-Time Sync Active');

  useEffect(() => {
    const v = params.get('view');
    if (v === 'circles') navigate('/circles');
    else if (v === 'events') navigate('/events');
    else if (v === 'users' || v === 'organisers' || v === 'members') setView('usershome');
    else if (v) setView(parseView(v));
  }, [params, navigate]);

  useEffect(() => {
    let on = true;
    (async () => {
      if (!getAccess()) {
        navigate('/auth?redirect=/admin');
        return;
      }
      try {
        const m = await api<Me>('/api/users/me');
        if (!on) return;
        setMe(m);
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) {
          clearTokens();
          navigate('/auth?redirect=/admin');
          return;
        }
        setGateErr(errMsg(e));
      } finally {
        if (on) setAuthLoading(false);
      }
    })();
    return () => {
      on = false;
    };
  }, [navigate]);

  useEffect(() => {
    if (me && me.id) setMeId(me.id);
  }, [me]);

  function signOut() {
    clearTokens();
    setMe(null);
    navigate('/auth');
  }

  function go(v: string) {
    if (v === 'circles') {
      navigate('/circles');
      return;
    }
    if (v === 'events') {
      navigate('/events');
      return;
    }
    setPageErr('');
    setView(v as AdminView);
  }

  /* Dashboard */
  const [range, setRange] = useState('30d');
  const [dash, setDash] = useState<DashboardData | null>(null);

  const loadDashboard = useCallback(async (r: string) => {
    try {
      setPageErr('');
      setSyncLbl('Syncing…');
      const d = await api<DashboardData>('/api/admin/dashboard?range=' + r);
      setDash(d);
      setSyncLbl('Real-Time Sync Active');
    } catch (e) {
      setPageErr(errMsg(e));
      setSyncLbl('Sync paused — ' + errMsg(e));
    }
  }, []);

  useEffect(() => {
    if (me && view === 'dashboard') void loadDashboard(range);
  }, [me, view, range, loadDashboard]);

  /* General chat */
  const [chatCircles, setChatCircles] = useState<ChatCircle[]>([]);
  const [lounge, setLounge] = useState<Lounge | null>(null);
  const [sel, setSel] = useState<{ id: string; lounge: boolean } | null>(null);
  const [msgs, setMsgs] = useState<ChatMsg[]>([]);
  const [leaders, setLeaders] = useState<Leader[]>([]);
  const [chatQ, setChatQ] = useState('');
  const [chatInput, setChatInput] = useState('');
  const [chatErr, setChatErr] = useState('');
  const [meId, setMeId] = useState<string | null>(null);
  const selRef = useRef<{ id: string; lounge: boolean } | null>(null);
  selRef.current = sel;

  const loadChatMsgs = useCallback(
    async (id: string, silent: boolean) => {
      try {
        if (!silent) setChatErr('');
        const m = await api<ChatMsg[]>('/api/circles/' + id + '/messages?size=50');
        setMsgs((Array.isArray(m) ? m : []).filter((x) => !x.deleted));
      } catch (e) {
        if (!silent) setChatErr(errMsg(e));
      }
    },
    [],
  );

  const selectChat = useCallback(
    async (s: { id: string; lounge: boolean }) => {
      setSel(s);
      await loadChatMsgs(s.id, true);
    },
    [loadChatMsgs],
  );

  const loadChat = useCallback(async () => {
    try {
      setChatErr('');
      const [channels, circles] = await Promise.all([
        api<{ lounge?: Lounge } | null>('/api/chat/channels').catch(() => null),
        api<ChatCircle[]>('/api/circles'),
      ]);
      const list = Array.isArray(circles) ? circles.filter((c) => !c.hidden) : [];
      setChatCircles(list);
      const lg = channels && channels.lounge ? channels.lounge : null;
      setLounge(lg);
      const cur = selRef.current;
      const next = cur
        ? cur
        : lg
          ? { id: lg.circleId, lounge: true }
          : list[0]
            ? { id: list[0].id, lounge: false }
            : null;
      if (next) await selectChat(next);
      try {
        const p = await api<Page<Leader>>('/api/users?size=100');
        const all = p.content || [];
        setLeaders((Array.isArray(all) ? all : []).filter((u) => u.role === 'ADMIN' || u.role === 'ORGANIZER'));
      } catch {
        /* leaders panel stays empty */
      }
    } catch (e) {
      setChatErr(errMsg(e));
    }
  }, [selectChat]);

  useEffect(() => {
    if (me && view === 'chat') void loadChat();
  }, [me, view, loadChat]);

  useEffect(() => {
    if (view !== 'chat' || !sel) return;
    const id = sel.id;
    const t = window.setInterval(() => void loadChatMsgs(id, true), 6000);
    return () => window.clearInterval(t);
  }, [view, sel, loadChatMsgs]);

  useEffect(() => {
    const box = document.getElementById('gchatMsgs');
    if (box) {
      requestAnimationFrame(() => {
        box.scrollTop = box.scrollHeight;
      });
    }
  }, [msgs, sel]);

  async function sendChat() {
    const v = chatInput.trim();
    if (!v || !sel) return;
    setChatInput('');
    try {
      await api('/api/circles/' + sel.id + '/messages', { method: 'POST', body: JSON.stringify({ content: v }) });
      await loadChatMsgs(sel.id, true);
    } catch (e) {
      setChatErr(errMsg(e));
      setChatInput(v);
    }
  }

  async function toggleChatLike(id: string) {
    try {
      const msg = msgs.find((m) => String(m.id) === String(id));
      if (msg?.likedByMe) {
        await api('/api/messages/' + id + '/like', { method: 'DELETE' }).catch(() => null);
      } else {
        await api('/api/messages/' + id + '/like', { method: 'POST' }).catch(() => null);
      }
      if (sel) await loadChatMsgs(sel.id, true);
    } catch (e) {
      setChatErr(errMsg(e));
    }
  }

  const chatFiltered = chatCircles.filter(
    (c) => !chatQ.trim() || (c.name || '').toLowerCase().includes(chatQ.toLowerCase()) || (c.city || '').toLowerCase().includes(chatQ.toLowerCase()),
  );
  const isLounge = !!sel?.lounge;
  const selCircle = sel && !sel.lounge ? chatCircles.find((x) => String(x.id) === String(sel.id)) : undefined;
  const headName = isLounge ? ((lounge?.name || 'Organisers Lounge').toUpperCase()) : (selCircle?.name || '—').toUpperCase();
  const headCount = isLounge ? (lounge?.memberCount ?? 0) : (selCircle?.memberCount ?? 1);
  const headSub = isLounge ? headCount + ' members • Organisers & Admin Exclusive' : headCount + (headCount === 1 ? ' member' : ' members');
  const headInit = isLounge ? '👑' : ((selCircle?.name || 'C')[0] || 'C').toUpperCase();

  /* Discussions */
  type BdTab = 'all' | 'my' | 'popular';
  const [bdTab, setBdTab] = useState<BdTab>('all');
  const [bdQ, setBdQ] = useState('');
  const [bdList, setBdList] = useState<Discussion[]>([]);
  const [bdMode, setBdMode] = useState<'list' | 'create' | 'view'>('list');
  const [bdTitle, setBdTitle] = useState('');
  const [bdAuthor, setBdAuthor] = useState('');
  const [bdDiscTitle, setBdDiscTitle] = useState('');
  const [bdGenre, setBdGenre] = useState('Fiction');
  const [bdCircleId, setBdCircleId] = useState('');
  const [bdExcerpt, setBdExcerpt] = useState('');
  const [bdCoverUrl, setBdCoverUrl] = useState('');
  const [bdCircles, setBdCircles] = useState<{ id: string; name?: string }[]>([]);
  const [bdCreating, setBdCreating] = useState(false);
  const [bdCreateErr, setBdCreateErr] = useState('');
  const [bdDetail, setBdDetail] = useState<DiscussionDetail | null>(null);
  const [bdContrib, setBdContrib] = useState('');
  const [replyFor, setReplyFor] = useState<string | null>(null);
  const [replyToName, setReplyToName] = useState('');

  const loadDiscussions = useCallback(async () => {
    try {
      setPageErr('');
      const data = await api<Discussion[]>('/api/discussions?query=' + encodeURIComponent(bdQ) + '&tab=' + bdTab);
      setBdList(Array.isArray(data) ? data : []);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }, [bdQ, bdTab]);

  useEffect(() => {
    if (me && view === 'discussions') {
      if (bdMode === 'list') void loadDiscussions();
      void api<{ id: string; name?: string }[]>('/api/circles')
        .then((cs) => {
          if (Array.isArray(cs)) setBdCircles(cs);
        })
        .catch(() => {});
    }
  }, [me, view, bdMode, loadDiscussions]);

  function onBdCoverFile(e: ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) {
      setBdCreateErr('Cover image must be under 5MB.');
      return;
    }
    const r = new FileReader();
    r.onload = () => {
      if (typeof r.result === 'string') setBdCoverUrl(r.result);
    };
    r.readAsDataURL(f);
  }

  async function postDiscussion() {
    setBdCreateErr('');
    const bookTitle = bdTitle.trim();
    const author = bdAuthor.trim();
    if (!bookTitle || !author) {
      setBdCreateErr('Book title and author are required.');
      return;
    }
    setBdCreating(true);
    const payload = {
      bookTitle,
      author,
      discussionTitle: bdDiscTitle.trim() || `${bookTitle} – Discussion & Analysis`,
      genre: bdGenre.trim() || 'General',
      tag: bdGenre.trim() || 'General',
      discussionExcerpt: bdExcerpt.trim() || `Discussion about ${bookTitle} by ${author}.`,
      coverImageUrl: bdCoverUrl.trim() || null,
      circleId: bdCircleId || null,
    };
    try {
      const created = await api<{ id: string }>('/api/discussions', { method: 'POST', body: JSON.stringify(payload) });
      setBdTitle('');
      setBdAuthor('');
      setBdDiscTitle('');
      setBdGenre('Fiction');
      setBdExcerpt('');
      setBdCoverUrl('');
      setBdCircleId('');
      setBdMode('list');
      await loadDiscussions();
      if (created?.id) {
        await openBd(created.id);
      }
    } catch (e) {
      setBdCreateErr(errMsg(e));
    } finally {
      setBdCreating(false);
    }
  }

  async function openBd(id: string) {
    try {
      setPageErr('');
      const d = await api<DiscussionDetail>('/api/discussions/' + id);
      setBdDetail(d);
      setBdMode('view');
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function postContrib() {
    const v = bdContrib.trim();
    if (!v || !bdDetail) return;
    try {
      await api('/api/discussions/' + bdDetail.id + '/threads', {
        method: 'POST',
        body: JSON.stringify({ title: 'Community Contribution', content: v }),
      });
      setBdContrib('');
      const f = await api<DiscussionDetail>('/api/discussions/' + bdDetail.id);
      setBdDetail(f);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function likeThread(tid: string) {
    if (!bdDetail) return;
    try {
      await api('/api/discussions/threads/' + tid + '/like', { method: 'POST' });
      const f = await api<DiscussionDetail>('/api/discussions/' + bdDetail.id);
      setBdDetail(f);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function postReply(tid: string) {
    const v = bdContrib.trim();
    if (!bdDetail || !v) return;
    try {
      await api('/api/discussions/threads/' + tid + '/comments', {
        method: 'POST',
        body: JSON.stringify({ content: v }),
      });
      setBdContrib('');
      setReplyFor(null);
      setReplyToName('');
      const f = await api<DiscussionDetail>('/api/discussions/' + bdDetail.id);
      setBdDetail(f);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function submitCommentOrReply() {
    if (replyFor) {
      await postReply(replyFor);
    } else {
      await postContrib();
    }
  }

  /* Reader-parity: discussion-level like + detail sort (same UX as reader) */
  const [bdSort, setBdSort] = useState<'newest' | 'oldest'>('newest');
  const [bdLiking, setBdLiking] = useState<Record<string, boolean>>({});

  async function likeDiscussionCard(id: string) {
    const key = String(id);
    setBdLiking((p) => ({ ...p, [key]: true }));
    try {
      const r = await api<{ liked: boolean; likesCount: number }>('/api/discussions/' + key + '/like', {
        method: 'POST',
      });
      setBdList((prev) =>
        prev.map((d) => (String(d.id) === key ? { ...d, likesCount: r.likesCount, userLiked: r.liked } : d)),
      );
      if (bdDetail && String(bdDetail.id) === key) {
        const f = await api<DiscussionDetail>('/api/discussions/' + key);
        setBdDetail(f);
      }
    } catch (e) {
      setPageErr(errMsg(e));
    } finally {
      setBdLiking((p) => ({ ...p, [key]: false }));
    }
  }

  const bdPopular = (() => {
    const pop: Record<string, { title?: string; author?: string; count: number; cover?: string }> = {};
    bdList.forEach((d) => {
      const k = (d.bookTitle || '').toLowerCase().trim() || '?';
      if (!pop[k]) pop[k] = { title: d.bookTitle, author: d.author, count: 0, cover: d.coverImageUrl };
      pop[k].count += (d.threads ? d.threads.length : 0) + (d.repliesCount || 0) + 1;
    });
    return Object.values(pop)
      .sort((a, b) => b.count - a.count)
      .slice(0, 6);
  })();

  /* Users home */
  type UhTab = 'all' | 'org' | 'mem';
  const [uhTab, setUhTab] = useState<UhTab>('all');
  const [uhPage, setUhPage] = useState(0);
  const [uhQInput, setUhQInput] = useState('');
  const [uhQ, setUhQ] = useState('');
  const [uhRole, setUhRole] = useState('');
  const [uhStatus, setUhStatus] = useState('');
  const [uhSort, setUhSort] = useState('createdAt,desc');
  const [uhSummary, setUhSummary] = useState<UhSummary | null>(null);
  const [uhRows, setUhRows] = useState<UserRow[]>([]);
  const [uhTotal, setUhTotal] = useState(0);
  const [uhSize, setUhSize] = useState(10);
  const [uhNumber, setUhNumber] = useState(0);
  const [uhSel, setUhSel] = useState<string[]>([]);
  const [uhCheckAll, setUhCheckAll] = useState(false);
  const [blockTarget, setBlockTarget] = useState<UserRow | null>(null);
  const [blockReason, setBlockReason] = useState('');
  const [uhDetail, setUhDetail] = useState<string | null>(null);
  const [uhMenu, setUhMenu] = useState<string | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setUhQ(uhQInput);
      setUhPage(0);
    }, 300);
    return () => window.clearTimeout(t);
  }, [uhQInput]);

  const loadUh = useCallback(async () => {
    try {
      setPageErr('');
      const role = uhTab === 'all' ? uhRole : uhTab === 'org' ? 'ORGANIZER' : 'READER';
      const p = new URLSearchParams({ page: String(uhPage), size: '10', sort: uhSort });
      if (uhQ.trim()) p.set('query', uhQ.trim());
      if (role) p.set('role', role);
      if (uhStatus) p.set('status', uhStatus);
      const [s, pg] = await Promise.all([api<UhSummary>('/api/admin/users/summary'), api<Page<UserRow>>('/api/users?' + p.toString())]);
      setUhSummary(s);
      setUhRows(pg.content || []);
      setUhTotal(pg.totalElements || 0);
      setUhSize(pg.size || 10);
      setUhNumber(pg.number || 0);
      setUhPage(pg.number || 0);
      setUhSel([]);
      setUhCheckAll(false);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }, [uhTab, uhPage, uhQ, uhRole, uhStatus, uhSort]);

  useEffect(() => {
    if (me && view === 'usershome') void loadUh();
  }, [me, view, loadUh]);

  function uhToggleOne(id: string, on: boolean) {
    setUhSel((prev) => (on ? [...new Set([...prev, id])] : prev.filter((x) => x !== id)));
  }

  function uhToggleAll(on: boolean) {
    setUhCheckAll(on);
    setUhSel(on ? uhRows.map((u) => u.id) : []);
  }

  async function uhSetActive(id: string, active: boolean) {
    if (active) {
      try {
        await api('/api/users/' + id + '/status', { method: 'PATCH', body: JSON.stringify({ active: true }) });
        await loadUh();
      } catch (e) {
        setPageErr(errMsg(e));
      }
      return;
    }
    if (meId && String(meId) === String(id)) {
      setPageErr('You cannot block yourself.');
      return;
    }
    try {
      const u = await api<UserRow>('/api/users/' + id);
      setBlockTarget(u);
      setBlockReason('');
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function uhConfirmBlock() {
    if (!blockTarget) return;
    try {
      await api('/api/users/' + blockTarget.id + '/status', {
        method: 'PATCH',
        body: JSON.stringify({ active: false, reason: blockReason.trim() || null }),
      });
      setBlockTarget(null);
      await loadUh();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function uhDemote(id: string) {
    if (meId && String(meId) === String(id)) {
      setPageErr('You cannot demote yourself.');
      return;
    }
    try {
      await api('/api/users/' + id, { method: 'PATCH', body: JSON.stringify({ role: 'READER' }) });
      await loadUh();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function uhPromote(id: string) {
    if (meId && String(meId) === String(id)) {
      setPageErr('You cannot change your own role.');
      return;
    }
    try {
      await api('/api/users/' + id, { method: 'PATCH', body: JSON.stringify({ role: 'ORGANIZER' }) });
      await loadUh();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function uhBulk(active: boolean) {
    if (!uhSel.length) return;
    try {
      for (const id of uhSel) {
        if (!active && meId && String(meId) === String(id)) continue;
        await api('/api/users/' + id + '/status', { method: 'PATCH', body: JSON.stringify({ active }) });
      }
      setUhSel([]);
      setUhCheckAll(false);
      await loadUh();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function uhExport() {
    try {
      const role = uhTab === 'all' ? uhRole : uhTab === 'org' ? 'ORGANIZER' : 'READER';
      const p = new URLSearchParams({ page: '0', size: '1000', sort: uhSort });
      if (uhQ.trim()) p.set('query', uhQ.trim());
      if (role) p.set('role', role);
      if (uhStatus) p.set('status', uhStatus);
      const pg = await api<Page<UserRow>>('/api/users?' + p.toString());
      downloadCsv('users.csv', pg.content || []);
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  function uhPill(r?: string) {
    if (r === 'ADMIN') return <span className="role-pill admin">Admin</span>;
    if (r === 'ORGANIZER') return <span className="role-pill organizer">Organiser</span>;
    return <span className="role-pill member">Member</span>;
  }

  const uhPages = Math.max(1, Math.ceil(uhTotal / uhSize));
  const uhPageNums: number[] = (() => {
    const start = Math.max(0, Math.min(uhNumber - 2, uhPages - 5));
    const end = Math.min(uhPages, start + 5);
    const out: number[] = [];
    for (let i = start; i < end; i++) out.push(i);
    return out;
  })();

  /* Analytics */
  const [ana, setAna] = useState<AnaRow[]>([]);
  useEffect(() => {
    if (!me || view !== 'analytics') return;
    (async () => {
      try {
        setPageErr('');
        const a = await api<{ circlePerformance?: AnaRow[] }>('/api/admin/analytics?range=30d');
        setAna(a.circlePerformance || []);
      } catch (e) {
        setPageErr(errMsg(e));
      }
    })();
  }, [me, view]);

  /* Settings */
  const [setName, setSetName] = useState('');
  const [setEmail, setSetEmail] = useState('');
  const [setRole, setSetRole] = useState('');
  const [setAvUrl, setSetAvUrl] = useState<string | null>(null);
  const [prefs, setPrefs] = useState<Record<string, boolean>>({});
  const [setSavedTick, setSetSavedTick] = useState('');
  const [pwOpen, setPwOpen] = useState(false);
  const [pwCur, setPwCur] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwErr, setPwErr] = useState('');
  const [allOpen, setAllOpen] = useState(false);

  const loadSettings = useCallback(async () => {
    try {
      setPageErr('');
      const [m, p] = await Promise.all([api<Me>('/api/users/me'), api<Record<string, boolean>>('/api/users/me/notification-preferences').catch(() => null)]);
      if (m.id) setMeId(m.id);
      setSetName(m.name || '');
      setSetEmail(m.email || '');
      setSetRole(m.role === 'ADMIN' ? 'Administrator' : m.role || '');
      setSetAvUrl(m.profilePictureUrl || null);
      setPrefs(p || {});
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }, []);

  useEffect(() => {
    if (me && view === 'settings') void loadSettings();
  }, [me, view, loadSettings]);

  function savedTick() {
    setSetSavedTick('Saved ✓');
    window.setTimeout(() => setSetSavedTick(''), 2000);
  }

  async function saveProfile() {
    try {
      const m = await api<Me>('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ name: setName.trim() || null, email: setEmail.trim() || null }),
      });
      if (m.id) setMeId(m.id);
      setMe(m);
      savedTick();
    } catch (e) {
      setPageErr(errMsg(e));
    }
  }

  async function togglePref(key: string, on: boolean) {
    setPrefs((prev) => ({ ...prev, [key]: on }));
    try {
      await api('/api/users/me/notification-preferences', { method: 'PUT', body: JSON.stringify({ [key]: on }) });
    } catch (e) {
      setPageErr(errMsg(e));
      setPrefs((prev) => ({ ...prev, [key]: !on }));
    }
  }

  function onPhotoFile(e: ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    (async () => {
      try {
        const fd = new FormData();
        fd.append('file', f);
        const r = await fetch(apiBase() + '/api/users/me/photo', {
          method: 'POST',
          headers: { Authorization: 'Bearer ' + (getAccess() || '') },
          body: fd,
        });
        const d = (await r.json().catch(() => ({}))) as { message?: string; profilePictureUrl?: string };
        if (!r.ok) throw new Error(d.message || 'Photo upload failed: ' + r.status);
        setSetAvUrl(d.profilePictureUrl || null);
        savedTick();
      } catch (err) {
        setPageErr(errMsg(err));
      }
    })();
    e.target.value = '';
  }

  async function changePassword() {
    setPwErr('');
    try {
      await api('/api/users/me/password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword: pwCur, newPassword: pwNew }),
      });
      setPwOpen(false);
      setPwCur('');
      setPwNew('');
      savedTick();
    } catch (e) {
      setPwErr(errMsg(e));
    }
  }

  async function signOutAll() {
    try {
      await api('/api/users/me/sign-out-all', { method: 'POST' });
    } catch {
      /* session ends regardless */
    }
    clearTokens();
    setMe(null);
    setAllOpen(false);
  }

  /* Growth chart geometry */
  const growth = dash?.growth || null;
  const chartMax = growth ? Math.max(1, ...growth.members, ...growth.events, ...growth.circles) : 1;
  const chartX = (i: number) => 28 + (i * (600 - 56)) / Math.max(1, (growth?.labels.length || 1) - 1);
  const chartY = (v: number) => 220 - 28 - (v / chartMax) * (220 - 56);
  const pts = (arr: number[]) => arr.map((v, i) => chartX(i) + ',' + chartY(v)).join(' ');

  const statCards = [
    { key: 'circles', label: 'Total Circles', value: dash?.stats.totalCircles, pct: dash?.stats.circlesChangePct, tint: '#F6E7DE', accent: '#A8452A', series: growth?.circles },
    { key: 'events', label: 'Total Events', value: dash?.stats.totalEvents, pct: dash?.stats.eventsChangePct, tint: '#E9E5F7', accent: '#7C6BD0', series: growth?.events },
    { key: 'members', label: 'Total Members', value: dash?.stats.totalMembers, pct: dash?.stats.membersChangePct, tint: '#E4EFE4', accent: '#3E8E4F', series: growth?.members },
    {
      key: 'discussions',
      label: 'Book Discussions',
      value: dash?.stats.totalDiscussions,
      pct: dash?.stats.discussionsChangePct,
      tint: '#F7ECCF',
      accent: '#C98A24',
      series: growth
        ? growth.members.map((_, i) => Math.round(((dash?.stats.totalDiscussions || 0) * (i + 1)) / growth.members.length))
        : [],
    },
  ];

  const donutC = 2 * Math.PI * 42;
  let donutOff = 0;
  const donutSegs = (dash?.circleDistribution?.items || []).slice(0, 6).map((it, i) => {
    const len = ((it.percent || 0) / 100) * donutC;
    const seg = {
      key: it.id || String(i),
      name: it.name || 'Circle',
      membersCount: it.membersCount ?? 0,
      percent: it.percent ?? 0,
      len,
      off: donutOff,
      color: DASH_PALETTE[i % DASH_PALETTE.length],
    };
    donutOff += len;
    return seg;
  });

  if (authLoading) {
    return (
      <div className="content" style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <p style={{ color: 'var(--muted)', fontSize: 15 }}>Checking admin credentials…</p>
      </div>
    );
  }

  if (!me || me.role !== 'ADMIN') {
    return (
      <div className="content" style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <div className="gate" style={{ maxWidth: 440, textAlign: 'center', padding: '36px 32px' }}>
          <div className="kicker">Admin Console</div>
          <h2 style={{ margin: '8px 0 12px' }}>Admin Access Required</h2>
          <p style={{ color: 'var(--muted)', margin: '0 0 24px', lineHeight: 1.5 }}>
            {gateErr || (me ? `You are signed in as ${me.role}. Admin privileges are required to access this console.` : 'Please sign in with an Administrator account.')}
          </p>
          <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
            {me && (
              <button className="btn" type="button" onClick={() => navigate(me.role === 'ORGANIZER' ? '/organizer' : '/reader')}>
                Go to {me.role === 'ORGANIZER' ? 'Organiser' : 'Reader'} Dashboard
              </button>
            )}
            <button className="btn ghost" type="button" onClick={() => { clearTokens(); navigate('/auth?redirect=/admin'); }}>
              Sign In with Admin Account
            </button>
          </div>
        </div>
      </div>
    );
  }

  const navBtn = (v: string, label: string) => (
    <button key={v} className="navbtn" data-view={v} aria-current={view === v ? 'page' : undefined} onClick={() => go(v)}>
      {label}
    </button>
  );

  return (
    <>
    <div className="app admin-app-page">
      <div className="side" aria-label="Admin navigation">
        <div className="brandrow">
          <span className="mark">📚</span>
          <div>
            <b>READER'S CIRCLE</b>
            <span>Admin Panel</span>
          </div>
        </div>
        <nav id="nav">
          {navBtn('dashboard', '🏠 Dashboard')}
          {navBtn('circles', '👥 Circles')}
          {navBtn('events', '📅 Events')}
          <div className="navgroup">Community</div>
          {navBtn('chat', '💬 General Chat')}
          {navBtn('discussions', '📖 Book Discussions')}
          {navBtn('recommend', '⭐ Book Recommendations')}
          <div className="navgroup">Commerce</div>
          {navBtn('books', '📚 Books')}
          {navBtn('orders', '🛒 Orders')}
          <div className="navgroup">Users</div>
          {navBtn('usershome', '👤 Users')}
          <div className="navgroup">Platform</div>
          {navBtn('analytics', '📊 Analytics')}
          {navBtn('settings', '⚙️ Settings')}
        </nav>
        <div className="sidefoot">
          <b>{me.name || me.email}</b>
          <span>{me.role === 'ADMIN' ? 'Administrator' : me.role}</span>
          <br />
          <button className="linkbtn" onClick={signOut}>
            Sign Out
          </button>
        </div>
      </div>

      <div className="main">
        <div className="topbar">
          <span className="sync">
            <span className="dot"></span>
            <span>{syncLbl}</span>
          </span>
          <span className="sp"></span>
          <input
            className="apibase"
            value={baseUrl}
            aria-label="API base URL"
            onChange={(e) => {
              setBaseUrl(e.target.value);
              setApiBase(e.target.value.replace(/\/+$/, ''));
            }}
          />
          <span className="userchip">
            <span className="avatar">{initialsOf(me.name || me.email)}</span>
            <span>{me.name || me.email}</span>
          </span>
        </div>

        <div className="content">
          {pageErr && <div className="err">{pageErr}</div>}

          <section className={'view' + (view === 'dashboard' ? ' on' : '')} id="v-dashboard">
            <div className="adash">
            <div className="adash-hero">
              <div className="adash-hero-main">
                <div className="adash-kicker">Admin Dashboard</div>
                <h1>Build a stronger reading community</h1>
                <p>Create circles, appoint organisers, manage the platform and help leaders connect through books.</p>
              </div>
              <div className="adash-hero-art" aria-hidden="true">
                <svg viewBox="0 0 170 130" width="170" height="130">
                  <path d="M120 70c0-18 8-30 22-36-6 16-7 27-11 36z" fill="#4E7A57" />
                  <path d="M116 70c-4-20-13-28-26-32 9 15 13 25 18 32z" fill="#6B9A72" />
                  <rect x="122" y="70" width="9" height="22" rx="3" fill="#3E6B4F" />
                  <path d="M108 90h38l-4 28a5 5 0 0 1-5 4h-20a5 5 0 0 1-5-4z" fill="#B5623C" />
                  <rect x="30" y="104" width="74" height="13" rx="3" fill="#5E3A22" />
                  <rect x="34" y="91" width="66" height="13" rx="3" fill="#B5623C" />
                  <rect x="38" y="78" width="58" height="13" rx="3" fill="#3E6B4F" />
                  <rect x="42" y="65" width="50" height="13" rx="3" fill="#C9A468" />
                </svg>
              </div>
            </div>

            <div className="adash-stats">
              {statCards.map((s) => (
                <div className="adash-stat" key={s.key}>
                  <span className="adash-stat-ic" style={{ background: s.tint, color: s.accent }}>
                    {statIcon(s.key)}
                  </span>
                  <div className="adash-stat-head">
                    <b className="num">{s.value ?? '–'}</b>
                    <span className={'adash-trend' + ((s.pct ?? 0) < 0 ? ' down' : '')}>
                      {(s.pct ?? 0) < 0 ? '↓' : '↑'} {Math.abs(s.pct ?? 0)}%
                    </span>
                  </div>
                  <span className="lbl">{s.label}</span>
                  {s.series && s.series.length > 1 && (
                    <svg className="adash-spark" viewBox="0 0 120 30" preserveAspectRatio="none" aria-hidden="true">
                      <polygon fill={s.accent} opacity="0.14" points={sparkGeometry(s.series).area} />
                      <polyline fill="none" stroke={s.accent} strokeWidth="2" points={sparkGeometry(s.series).line} />
                    </svg>
                  )}
                </div>
              ))}
            </div>

            <div className="adash-row2">
              <div className="adash-panel">
                <div className="adash-phead">
                  <span className="adash-pic" style={{ background: '#F7ECD2', color: '#B4832A' }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                      <path d="M6 3h12v2h3v3a4 4 0 0 1-4 4h-.4A6 6 0 0 1 13 16.6V19h3v2H8v-2h3v-2.4A6 6 0 0 1 7.4 12H7a4 4 0 0 1-4-4V5h3zM5 7v1a2 2 0 0 0 2 2V7zm14 0h-2v3a2 2 0 0 0 2-2z" />
                    </svg>
                  </span>
                  <div>
                    <h3>Top Circles</h3>
                    <p>Circles with highest activity and engagement.</p>
                  </div>
                </div>
                <div className="adash-tablewrap">
                  <table className="adash-table adash-top">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Circle</th>
                        <th>Members</th>
                        <th>Discussions</th>
                        <th>Events</th>
                        <th>Activity (7d)</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dash?.topCircles?.length ? (
                        dash.topCircles.map((c, i) => (
                          <tr key={c.id || i}>
                            <td>
                              <span className={'adash-rank r' + (i + 1)}>{i + 1}</span>
                            </td>
                            <td>
                              <div className="adash-circ">
                                <img alt="" src={resolveMedia(c.coverImageUrl) || COVER_PLACEHOLDER} />
                                <div>
                                  <b>{c.name}</b>
                                  <span>{c.category || 'General'}</span>
                                </div>
                              </div>
                            </td>
                            <td className="adash-numcell">
                              <b>{c.membersCount ?? 0}</b>
                              {!!c.membersDelta && <span className="adash-up">↑ {c.membersDelta}</span>}
                            </td>
                            <td className="adash-numcell">
                              <b>{c.discussionsCount ?? 0}</b>
                              {!!c.discussionsDelta && <span className="adash-up">↑ {c.discussionsDelta}</span>}
                            </td>
                            <td className="adash-numcell">
                              <b>{c.eventsCount ?? 0}</b>
                              {!!c.eventsDelta && <span className="adash-up">↑ {c.eventsDelta}</span>}
                            </td>
                            <td>
                              <div className="adash-bars">
                                {activityBars(c.activity7d ?? 0).map((h, bi) => (
                                  <span key={bi} style={{ height: h + '%' }} />
                                ))}
                              </div>
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={6} className="adash-empty">No circles yet — create one to get started.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="adash-panel">
                <div className="adash-phead">
                  <span className="adash-pic" style={{ background: '#E9E5F7', color: '#6F5FD0' }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                      <path d="M7 2h2v2h6V2h2v2h2a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2zM5 9v10h14V9z" />
                    </svg>
                  </span>
                  <div>
                    <h3>Upcoming Events</h3>
                    <p>Next events across all circles.</p>
                  </div>
                </div>
                <div className="adash-tablewrap">
                  <table className="adash-table adash-evt">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Event</th>
                        <th>Circle</th>
                        <th>Registrations</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dash?.upcomingEvents?.length ? (
                        dash.upcomingEvents.map((e, i) => (
                          <tr key={e.id || i}>
                            <td>
                              <div className="adash-datebox">
                                <b>{dayNum(e.eventDate)}</b>
                                <span>{monShort(e.eventDate)}</span>
                              </div>
                            </td>
                            <td>
                              <div className="adash-evt-name">
                                <b>{e.title}</b>
                                <span>{[e.eventTime ? e.eventTime.slice(0, 5) : '', e.venue || e.location].filter(Boolean).join(' · ')}</span>
                              </div>
                            </td>
                            <td>
                              <div className="adash-circ small">
                                <img alt="" src={resolveMedia(e.circleCoverImageUrl) || COVER_PLACEHOLDER} />
                                <div>
                                  <b>{e.circleName}</b>
                                </div>
                              </div>
                            </td>
                            <td>
                              <div className="adash-reg">
                                <span>
                                  {e.registeredCount ?? 0} / {e.capacity || 0}
                                </span>
                                <div className="adash-prog">
                                  <i style={{ width: regPercent(e) + '%' }} />
                                </div>
                              </div>
                            </td>
                            <td>
                              <span className="adash-status">Upcoming</span>
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={5} className="adash-empty">No upcoming events — publish one from a circle.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <div className="adash-row3">
              <div className="adash-panel">
                <div className="adash-phead">
                  <span className="adash-pic" style={{ background: '#E4EFE4', color: '#3E8E4F' }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                      <path d="M4 20V4h2v16zm4 0v-7h2v7zm4 0V8h2v12zm4 0V12h2v8z" />
                    </svg>
                  </span>
                  <div>
                    <h3>Platform Growth</h3>
                    <p>Members, events and circles created over time.</p>
                  </div>
                  <select className="adash-range" aria-label="Growth range" value={range} onChange={(e) => setRange(e.target.value)}>
                    <option value="30d">Last 30 Days</option>
                    <option value="3m">Last 3 Months</option>
                    <option value="1y">Last 1 Year</option>
                  </select>
                </div>
                <svg className="adash-linechart" role="img" aria-label="Growth chart" viewBox="0 0 600 220">
                  {[0, 0.33, 0.66, 1].map((f, k) => {
                    const v = chartMax * f;
                    const y = chartY(v);
                    return (
                      <g key={k}>
                        <line x1="28" y1={y} x2="600" y2={y} stroke="#EDE5D8" strokeWidth="1" />
                        <text x="23" y={y + 3} fontSize="10" fill="#B4A896" textAnchor="end">
                          {Math.round(v)}
                        </text>
                      </g>
                    );
                  })}
                  {growth && (
                    <>
                      <polygon fill="#8A3A22" opacity="0.1" points={'28,' + (220 - 28) + ' ' + pts(growth.members) + ' ' + (600 - 28) + ',' + (220 - 28)} />
                      <polyline fill="none" stroke="#8A3A22" strokeWidth="2.5" points={pts(growth.members)} />
                      <polyline fill="none" stroke="#3E6B4F" strokeWidth="2.5" points={pts(growth.events)} />
                      <polyline fill="none" stroke="#E08A3C" strokeWidth="2.5" points={pts(growth.circles)} />
                      {growth.labels.map((l, i) => (
                        <text key={i} x={chartX(i)} y={220 - 6} fontSize="10" fill="#B4A896" textAnchor="middle">
                          {l}
                        </text>
                      ))}
                    </>
                  )}
                </svg>
                <div className="adash-legend">
                  <span>
                    <i style={{ background: '#8A3A22' }}></i>Members
                  </span>
                  <span>
                    <i style={{ background: '#3E6B4F' }}></i>Events
                  </span>
                  <span>
                    <i style={{ background: '#E08A3C' }}></i>Circles
                  </span>
                </div>
              </div>

              <div className="adash-panel">
                <div className="adash-phead">
                  <span className="adash-pic" style={{ background: '#F6E7DE', color: '#A8452A' }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                      <path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm1 5v4.6l3.3 2-1 1.7L11 14V8z" />
                    </svg>
                  </span>
                  <div>
                    <h3>Recent Activity</h3>
                    <p>Latest actions across the platform.</p>
                  </div>
                </div>
                <ul className="adash-feed">
                  {dash?.recentActivity?.length ? (
                    dash.recentActivity.map((a, i) => (
                      <li key={i}>
                        <span className="adash-feed-ic">{a.icon || '•'}</span>
                        <div>
                          <b>{a.description}</b>
                          <span>{timeAgo(a.createdAt)}</span>
                        </div>
                      </li>
                    ))
                  ) : (
                    <li className="adash-empty">No recent activity yet.</li>
                  )}
                </ul>
              </div>

              <div className="adash-panel">
                <div className="adash-phead">
                  <span className="adash-pic" style={{ background: '#E9E5F7', color: '#6F5FD0' }}>
                    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
                      <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2v8l6 3.5-1 1.7L11 13V4z" />
                    </svg>
                  </span>
                  <div>
                    <h3>Circle Distribution</h3>
                    <p>Members across circles.</p>
                  </div>
                </div>
                <div className="adash-donut-wrap">
                  <svg className="adash-donut" viewBox="0 0 120 120" role="img" aria-label="Members across circles">
                    <circle cx="60" cy="60" r="42" fill="none" stroke="#F0E9DC" strokeWidth="16" />
                    {donutSegs.map((s) => (
                      <circle
                        key={s.key}
                        cx="60"
                        cy="60"
                        r="42"
                        fill="none"
                        stroke={s.color}
                        strokeWidth="16"
                        strokeDasharray={s.len + ' ' + (donutC - s.len)}
                        strokeDashoffset={-s.off}
                        transform="rotate(-90 60 60)"
                      />
                    ))}
                    <text x="60" y="57" textAnchor="middle" className="adash-donut-total">
                      {dash?.circleDistribution?.total ?? 0}
                    </text>
                    <text x="60" y="74" textAnchor="middle" className="adash-donut-sub">
                      Total Members
                    </text>
                  </svg>
                  <ul className="adash-legend-list">
                    {donutSegs.length ? (
                      donutSegs.map((s) => (
                        <li key={s.key}>
                          <i style={{ background: s.color }} />
                          <span className="nm">{s.name}</span>
                          <span className="ct">{s.membersCount}</span>
                          <span className="pc">{s.percent}%</span>
                        </li>
                      ))
                    ) : (
                      <li className="adash-empty">No circles yet.</li>
                    )}
                  </ul>
                </div>
              </div>
            </div>
            </div>
          </section>

          <section className={'view' + (view === 'chat' ? ' on' : '')} id="v-chat">
            <div className="gchat-banner">
              <div className="gchat-hero">
                <span className="gchat-hero-ic" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 6.5C10.5 5 8.3 4.3 5 4.3v13.4c3.3 0 5.5.7 7 2.2 1.5-1.5 3.7-2.2 7-2.2V4.3c-3.3 0-5.5.7-7 2.2Z" />
                    <path d="M12 6.5v13.4" />
                  </svg>
                </span>
                <div>
                  <span className="gchat-hero-kick">The Reading Room</span>
                  <h2>General chat</h2>
                  <p>Talk books, share notes, and keep every circle in sync.</p>
                </div>
              </div>
              <div className="gchat-shelf" aria-hidden="true">
                <span />
                <span />
                <span />
                <span />
                <span />
              </div>
            </div>
            <div className="gchat-grid">
              <aside aria-label="Channels">
                <div className="gchat-left">
                  <div className="gchat-search">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                      <circle cx="11" cy="11" r="7" />
                      <path d="m20 20-3.2-3.2" />
                    </svg>
                    <input value={chatQ} onChange={(e) => setChatQ(e.target.value)} placeholder="Search circles..." aria-label="Search circles" />
                  </div>
                  <div className="gchat-seclbl">ORGANISERS &amp; ADMIN</div>
                  {lounge && (
                    <button
                      className={'gchat-chan lounge' + (isLounge ? ' on' : '')}
                      type="button"
                      onClick={() => void selectChat({ id: lounge.circleId, lounge: true })}
                    >
                      <span className="gchat-badge gold">👑</span>
                      <span>
                        <b>ORGANISERS LOUNGE</b>
                        <small>
                          {lounge.memberCount} {lounge.memberCount === 1 ? 'member • Leadership' : 'members • Leadership'}
                        </small>
                      </span>
                      <span className="gchat-tick" style={{ visibility: isLounge ? 'visible' : 'hidden' }}>
                        ✓
                      </span>
                    </button>
                  )}
                  <div className="gchat-seclbl">COMMUNITY CIRCLES</div>
                  <div className="gchat-chans-scroll">
                    {chatFiltered.length ? (
                      chatFiltered.map((c) => {
                        const on = sel !== null && !sel.lounge && String(sel.id) === String(c.id);
                        const n = c.memberCount == null ? 1 : c.memberCount;
                        return (
                          <button key={c.id} type="button" className={'gchat-chan' + (on ? ' on' : '')} onClick={() => void selectChat({ id: c.id, lounge: false })}>
                            <span className="gchat-badge">{((c.name || 'C')[0] || 'C').toUpperCase()}</span>
                            <span>
                              <b>{(c.name || '').toUpperCase()}</b>
                              <small>
                                {n} {n === 1 ? 'member' : 'members'}
                              </small>
                            </span>
                            {on && <span className="gchat-tick">✓</span>}
                          </button>
                        );
                      })
                    ) : (
                      <p className="sub">No circles found.</p>
                    )}
                  </div>
                </div>
              </aside>
              <div className="gchat-mid" aria-label="Conversation">
                <div className="gchat-head">
                  <span className={'gchat-badge' + (isLounge ? ' gold' : '')}>{headInit}</span>
                  <div>
                    <h3>{headName}</h3>
                    <small>{headSub}</small>
                  </div>
                  <span className="gchat-dot" title="Connected"></span>
                </div>
                <div className="gchat-msgs" id="gchatMsgs" aria-live="polite">
                  {msgs.length ? (
                    msgs.map((x) => {
                      const own = meId !== null && String(x.senderId) === String(meId);
                      const isLiked = !!x.likedByMe;
                      const n = Number(x.likeCount || 0);
                      let t = '';
                      try {
                        t = new Date(x.sentAt || '').toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
                      } catch {
                        t = '';
                      }
                      return (
                        <div className={'gchat-row' + (own ? ' own' : '')} key={x.id}>
                          <span className="gchat-av">
                            {x.senderAvatarUrl ? (
                              <img src={resolveMedia(x.senderAvatarUrl)} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                            ) : (
                              ((x.senderName || '?')[0] || '?').toUpperCase()
                            )}
                          </span>
                          <div>
                            <div className="gchat-meta" title={timeAgo(x.sentAt)}>
                              <b>{x.senderName || 'Member'}</b>
                              <span>{t}</span>
                            </div>
                            <div className="gchat-bub">
                              <p>{x.content}</p>
                              <button type="button" className={'gchat-like' + (isLiked ? ' liked' : '')} onClick={() => void toggleChatLike(x.id)}>
                                ♡ {n}
                              </button>
                            </div>
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    <p className="gchat-empty">No messages yet — say hello to the circle.</p>
                  )}
                </div>
                {chatErr && (
                  <div className="err" style={{ margin: '8px 16px 0' }}>
                    {chatErr}
                  </div>
                )}
                <div className="gchat-comp">
                  <div className="gchat-pill">
                    <button
                      className="gchat-tool"
                      type="button"
                      title="Add emoji"
                      onClick={() => {
                        setChatInput((v) => v + ' 😊');
                      }}
                    >
                      😊
                    </button>
                    <input
                      value={chatInput}
                      onChange={(e) => setChatInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') void sendChat();
                      }}
                      placeholder={isLounge ? 'Message organisers & admin...' : 'Type a message...'}
                      maxLength={2000}
                      aria-label="Type a message"
                      autoComplete="off"
                    />
                    <button className="gchat-tool" type="button" title="Attach file">
                      📎
                    </button>
                    <button className="gchat-tool" type="button" title="Send image">
                      🖼️
                    </button>
                    <button className="gchat-send" type="button" title="Send message" onClick={() => void sendChat()}>
                      ➤
                    </button>
                  </div>
                </div>
              </div>
              <aside className="gchat-right" aria-label="Circle information">
                <div className="gchat-card">
                  <h3>{isLounge ? 'Organisers Group' : 'Circle Information'}</h3>
                  <div className={'gchat-big' + (isLounge ? ' gold' : '')}>{isLounge ? '👑' : headInit}</div>
                  <p className="gchat-cname">{headName}</p>
                  <p className="gchat-csub">{isLounge ? 'Leadership & Admin Channel' : headSub}</p>
                  <div className="gchat-stat">
                    <span>{isLounge ? 'Organisers & Admin' : 'Members'}</span>
                    <b>{headCount}</b>
                  </div>
                  {isLounge && (
                    <div className="gchat-leaders">
                      <h4>Active leaders ({leaders.length})</h4>
                      <div>
                        {leaders.map((u, i) => (
                          <div className="gchat-leader" key={u.id || i}>
                            <span className="gchat-lav">{((u.name || u.email || '?')[0] || '?').toUpperCase()}</span>
                            <span>
                              <b>{u.name || u.email}</b>
                              <small>{u.email || ''}</small>
                            </span>
                            <span className={'gchat-role ' + (u.role === 'ADMIN' ? 'admin' : 'organizer')}>{u.role}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  {!isLounge && (
                    <div className="gchat-quote">
                      <span style={{ color: '#D89185', fontFamily: 'Georgia,serif', fontSize: 22, lineHeight: 1 }}>“</span>
                      <blockquote>“Books are a uniquely portable magic.”</blockquote>
                      <cite>— Stephen King</cite>
                    </div>
                  )}
                </div>
                <div className="gchat-card">
                  <h3>{isLounge ? 'Organisers Guidelines' : 'Circle Guidelines'}</h3>
                  <ul className="gchat-rules">
                    {(isLounge ? LOUNGE_RULES : CIRCLE_RULES).map((t) => (
                      <li key={t}>
                        <span className="gchat-ok">✓</span>
                        <span>{t}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </aside>
            </div>
          </section>

          <section className={'view' + (view === 'discussions' ? ' on' : '')} id="v-discussions">
            {(bdMode === 'list' || bdMode === 'create') && (
              <div className="rd-admin-feed">
                <div className="feed-header">
                  <div className="feed-header-left">
                    <h1>Discussions</h1>
                    <p>Explore discussions and share your thoughts with fellow readers.</p>
                  </div>
                  <button className="start-disc-btn" onClick={() => setBdMode('create')}>
                    ➕ Start a Discussion
                  </button>
                </div>
                <div className="rd-admin-filters" role="group" aria-label="Discussion tabs">
                  {(
                    [
                      ['all', '💬 All Discussions'],
                      ['my', '👤 My Discussions'],
                      ['popular', '🕐 Popular'],
                    ] as Array<[BdTab, string]>
                  ).map(([t, lbl]) => (
                    <button
                      key={t}
                      className={'rd-admin-pill' + (bdTab === t ? ' on' : '')}
                      aria-pressed={bdTab === t ? 'true' : 'false'}
                      onClick={() => setBdTab(t)}
                    >
                      {lbl}
                    </button>
                  ))}
                  <input
                    className="rd-admin-search"
                    value={bdQ}
                    onChange={(e) => setBdQ(e.target.value)}
                    placeholder="🔍 Search discussions..."
                    aria-label="Search discussions"
                  />
                </div>
                <div className="rd-admin-grid">
                  <main className="feed" aria-live="polite">
                    {bdList.length ? (
                      bdList.map((d) => {
                        const key = String(d.id);
                        const title = d.discussionTitle || d.bookTitle || 'Discussion';
                        const tags = d.tags && d.tags.length ? d.tags.slice(0, 4) : d.tag ? [d.tag] : [];
                        return (
                          <article className="dcard" key={d.id}>
                            <img
                              className="dcover"
                              src={d.coverImageUrl || RD_PLACEHOLDER}
                              alt={'Cover of ' + (d.bookTitle || 'book')}
                              onError={(e) => rdCoverError(e, RD_PLACEHOLDER)}
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
                                <button className="kebab" aria-label="More actions">
                                  ···
                                </button>
                              </div>
                              <div className="gpills">
                                {rdGenrePills(d).map((t, i) => (
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
                                  {tags
                                    .map(rdHashLabel)
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
                                      disabled={!!bdLiking[key]}
                                      onClick={() => void likeDiscussionCard(key)}
                                    >
                                      <span className="h">♥</span> <span>{d.likesCount ?? 0}</span>
                                    </button>
                                    <span>💬 {d.repliesCount ?? 0}</span>
                                  </div>
                                  <button className="viewbtn" onClick={() => void openBd(d.id)}>
                                    View Discussion <span aria-hidden="true">→</span>
                                  </button>
                                </div>
                              </div>
                            </div>
                          </article>
                        );
                      })
                    ) : (
                      <div className="notice">
                        <b>No discussions yet.</b>
                        <br />
                        Start one with the button above — it appears for readers and organisers too.
                      </div>
                    )}
                  </main>
                  <aside className="railcards" aria-label="Trending and stats">
                    <section className="panel" aria-labelledby="aTrend">
                      <h3 id="aTrend">🔥 Trending Books</h3>
                      <div>
                        {bdPopular.length ? (
                          bdPopular.slice(0, 3).map((p, i) => (
                            <div className="rank" key={i}>
                              <span className="n">{i + 1}</span>
                              {p.cover && <img src={p.cover} alt={'Cover of ' + (p.title || '')} />}
                              <div>
                                <b>{p.title || ''}</b>
                                <span>{p.author || ''}</span>
                                <span>{p.count} discussions</span>
                              </div>
                            </div>
                          ))
                        ) : (
                          <div className="empty">No trending books yet.</div>
                        )}
                      </div>
                    </section>
                    <section className="panel" aria-labelledby="aStats">
                      <h3 id="aStats">⭐ Community Stats</h3>
                      <div>
                        <div className="rank">
                          <span className="n">💬</span>
                          <div>
                            <b>{bdList.length}</b>
                            <span>Discussions on the platform</span>
                          </div>
                        </div>
                        <div className="rank">
                          <span className="n">👥</span>
                          <div>
                            <b>{new Set(bdList.map((x) => (x.createdBy || '').toLowerCase())).size}</b>
                            <span>Active contributors</span>
                          </div>
                        </div>
                      </div>
                    </section>
                  </aside>
                </div>
              </div>
            )}
            {bdMode === 'create' && (
              <div className="disc-modal-overlay" onClick={() => !bdCreating && setBdMode('list')}>
                <div className="disc-modal" onClick={(e) => e.stopPropagation()}>
                  <div className="disc-modal-head">
                    <h2>Start a Discussion</h2>
                    <button
                      className="disc-modal-close"
                      onClick={() => !bdCreating && setBdMode('list')}
                      aria-label="Close dialog"
                    >
                      ✕
                    </button>
                  </div>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void postDiscussion();
                    }}
                  >
                    <div className="disc-modal-body">
                      {bdCreateErr && <div className="err">{bdCreateErr}</div>}
                      <div className="disc-form-row">
                        <div className="disc-form-group">
                          <label>Book Title *</label>
                          <input
                            type="text"
                            placeholder="e.g. Dune"
                            value={bdTitle}
                            onChange={(e) => setBdTitle(e.target.value)}
                            required
                          />
                        </div>
                        <div className="disc-form-group">
                          <label>Author *</label>
                          <input
                            type="text"
                            placeholder="e.g. Frank Herbert"
                            value={bdAuthor}
                            onChange={(e) => setBdAuthor(e.target.value)}
                            required
                          />
                        </div>
                      </div>

                      <div className="disc-form-group">
                        <label>Discussion Title (Optional)</label>
                        <input
                          type="text"
                          placeholder="e.g. Dune - A World Beyond Our Own"
                          value={bdDiscTitle}
                          onChange={(e) => setBdDiscTitle(e.target.value)}
                        />
                      </div>

                      <div className="disc-form-row">
                        <div className="disc-form-group">
                          <label>Genre / Category</label>
                          <select value={bdGenre} onChange={(e) => setBdGenre(e.target.value)}>
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
                          <select value={bdCircleId} onChange={(e) => setBdCircleId(e.target.value)}>
                            <option value="">General Community</option>
                            {bdCircles.map((c) => (
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
                          value={bdExcerpt}
                          onChange={(e) => setBdExcerpt(e.target.value)}
                        />
                      </div>

                      <div className="disc-form-group">
                        <label>Cover Image (Optional)</label>
                        {bdCoverUrl ? (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                            <img
                              src={bdCoverUrl}
                              alt="Cover preview"
                              style={{ width: 64, height: 80, objectFit: 'cover', borderRadius: 8, border: '1px solid #E5DED2' }}
                            />
                            <label className="disc-cancel-btn" style={{ cursor: 'pointer', display: 'inline-block' }}>
                              Change Image
                              <input type="file" accept=".jpg,.jpeg,.png,.webp" hidden onChange={onBdCoverFile} />
                            </label>
                            <button
                              type="button"
                              className="disc-cancel-btn"
                              style={{ color: '#8F2B22' }}
                              onClick={() => setBdCoverUrl('')}
                            >
                              Remove
                            </button>
                          </div>
                        ) : (
                          <div>
                            <input type="file" accept=".jpg,.jpeg,.png,.webp" onChange={onBdCoverFile} />
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
                        onClick={() => setBdMode('list')}
                        disabled={bdCreating}
                      >
                        Cancel
                      </button>
                      <button type="submit" className="disc-submit-btn" disabled={bdCreating}>
                        {bdCreating ? 'Publishing…' : 'Publish Discussion'}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}
            {bdMode === 'view' && bdDetail && (
              <div className="rdet-wrap rd-admin-detail">
                <button
                  className="backlink"
                  onClick={() => {
                    setBdMode('list');
                    void loadDiscussions();
                  }}
                >
                  <span aria-hidden="true">←</span>
                  <span>Back to Discussions</span>
                </button>

                <div className="rdet-grid">
                  <main className="rdet-main">
                    <section className="hero">
                      <div className="hero-cover-container">
                        <img
                          className="hero-cover"
                          src={bdDetail.coverImageUrl || RD_FALLBACK}
                          alt={'Cover of ' + (bdDetail.bookTitle || 'book')}
                          onError={(e) => rdCoverError(e, RD_FALLBACK)}
                        />
                        <div className="cover-spine-edge" aria-hidden="true" />
                      </div>
                      <div className="hero-body">
                        <span className="hero-eyebrow">Book Discussion</span>
                        <h1>{bdDetail.bookTitle || bdDetail.discussionTitle || 'Discussion'}</h1>
                        <p className="hero-author">
                          by <span className="author-name">{bdDetail.author || 'Unknown Author'}</span>
                        </p>
                        <div className="hero-meta">
                          {bdDetail.genre && <span className="hero-badge">📖 {bdDetail.genre}</span>}
                          {bdDetail.pageCount != null && <span className="hero-badge">📄 {bdDetail.pageCount} pages</span>}
                          {bdDetail.publicationYear != null && (
                            <span className="hero-badge">📅 Published: {bdDetail.publicationYear}</span>
                          )}
                        </div>
                        {(bdDetail.discussionTitle || bdDetail.discussionExcerpt) && (
                          <div className="post-block">
                            <div className="post-byline">
                              <span className="face xs">{initialsOf(bdDetail.bookTitle)}</span>
                              <span className="post-byline-tx">
                                <b>{me?.name || 'Admin'}</b>
                                <span className="dot-sep">·</span>
                                <span>in Reading Circle</span>
                              </span>
                            </div>
                            {bdDetail.discussionTitle && <h2 className="post-title">{bdDetail.discussionTitle}</h2>}
                            {bdDetail.discussionExcerpt && <p className="post-excerpt">{bdDetail.discussionExcerpt}</p>}
                            <div className="post-actions">
                              <button
                                className="mini-like"
                                onClick={() => void likeDiscussionCard(bdDetail.id)}
                                aria-label="Like this discussion"
                              >
                                ♥ <span>{(bdDetail as Discussion).likesCount ?? 0}</span>
                              </button>
                              <span className="post-count">💬 {(bdDetail.threads || []).length} comments</span>
                            </div>
                          </div>
                        )}
                      </div>
                    </section>

                    <section className="clist-card">
                      <div className="clist-head">
                        <div className="clist-title-wrap">
                          <h2>Comments</h2>
                          <span className="count-pill">{(bdDetail.threads || []).length}</span>
                        </div>
                        <div className="sortwrap">
                          <label htmlFor="bd-comment-sort" className="sort-label">
                            Sort:
                          </label>
                          <div className="select-container">
                            <select
                              id="bd-comment-sort"
                              value={bdSort}
                              onChange={(e) => setBdSort(e.target.value as 'oldest' | 'newest')}
                              aria-label="Sort comments"
                            >
                              <option value="newest">Newest First</option>
                              <option value="oldest">Oldest First</option>
                            </select>
                          </div>
                        </div>
                      </div>

                      {(bdDetail.threads || []).length === 0 ? (
                        <div className="empty-notice">
                          <div className="empty-icon">💬</div>
                          <p>No comments yet — be the first to start the discussion below.</p>
                        </div>
                      ) : (
                        <div className="threads-container">
                          {(bdSort === 'newest' ? [...(bdDetail.threads || [])].reverse() : bdDetail.threads || []).map(
                            (t) => (
                              <div className="thread-item" key={t.id}>
                                <div className="cmt">
                                  <span className="face">{initialsOf(t.author)}</span>
                                  <div className="rbody">
                                    <div className="rhead">
                                      <b>{t.author || 'Member'}</b>
                                      <span className="dot-sep">·</span>
                                      <span className="timestamp">{t.timeAgo || ''}</span>
                                    </div>
                                    <p>{t.content || ''}</p>
                                    <div className="rfoot">
                                      <button className="mini-like" onClick={() => void likeThread(t.id)}>
                                        ♥ <span>{t.likesCount ?? 0}</span>
                                      </button>
                                      <button
                                        className={'mini-reply' + (replyFor === t.id ? ' on' : '')}
                                        onClick={() => {
                                          if (replyFor === t.id) {
                                            setReplyFor(null);
                                            setReplyToName('');
                                          } else {
                                            setReplyFor(t.id);
                                            setReplyToName(t.author || 'Member');
                                            window.setTimeout(() => {
                                              const el = document.getElementById('bdContribText');
                                              if (el) {
                                                el.focus();
                                                el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                                              }
                                            }, 50);
                                          }
                                        }}
                                      >
                                        ↩ <span>Reply</span>
                                      </button>
                                      <span className="post-count">💬 {t.repliesCount ?? (t.comments || []).length}</span>
                                    </div>
                                  </div>
                                </div>
                                {(t.comments || []).length > 0 && (
                                  <div className="kidbox">
                                    {(t.comments || []).map((c, i) => (
                                      <div className="cmt sub" key={i}>
                                        <span className="face sm">{initialsOf(c.author)}</span>
                                        <div className="rbody">
                                          <div className="rhead">
                                            <b>{c.author || 'Member'}</b>
                                            <span className="dot-sep">·</span>
                                            <span className="timestamp">{c.createdAt || ''}</span>
                                          </div>
                                          <p>{c.content || ''}</p>
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </div>
                            ),
                          )}
                        </div>
                      )}
                    </section>

                    <section className="composer" style={{ flexDirection: 'column', alignItems: 'stretch', gap: 6 }}>
                      {replyFor && (
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
                            ↩ Replying to <b>@{replyToName || 'Member'}</b>
                          </span>
                          <button
                            type="button"
                            style={{
                              background: 'none',
                              border: 0,
                              color: 'var(--faint)',
                              cursor: 'pointer',
                              fontWeight: 600,
                              fontSize: 12,
                            }}
                            onClick={() => {
                              setReplyFor(null);
                              setReplyToName('');
                            }}
                          >
                            ✕ Cancel
                          </button>
                        </div>
                      )}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%' }}>
                        <span className="composer-face" title="Your avatar">
                          {initialsOf(me?.name || me?.email)}
                        </span>
                        <div className="cwrap">
                          <input
                            id="bdContribText"
                            value={bdContrib}
                            onChange={(e) => setBdContrib(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' && !e.shiftKey && bdContrib.trim()) {
                                e.preventDefault();
                                void submitCommentOrReply();
                              } else if (e.key === 'Escape' && replyFor) {
                                setReplyFor(null);
                                setReplyToName('');
                              }
                            }}
                            placeholder={replyFor ? `Write a reply to @${replyToName || 'Member'}...` : 'Write a comment...'}
                            aria-label={replyFor ? 'Write a reply' : 'Write a comment'}
                          />
                        </div>
                        <button
                          className="postbtn"
                          disabled={!bdContrib.trim()}
                          onClick={() => void submitCommentOrReply()}
                          aria-label={replyFor ? 'Post reply' : 'Post comment'}
                        >
                          <span>{replyFor ? 'Post Reply' : 'Post Comment'}</span>
                          <span aria-hidden="true">➤</span>
                        </button>
                      </div>
                    </section>
                  </main>

                  <aside className="rdet-rail" aria-label="Book and community info">
                    <section className="panel book-panel">
                      <div className="panel-header">
                        <h3>Book Details</h3>
                      </div>
                      <div className="bookrow">
                        <div className="bookrow-thumb">
                          <img
                            src={bdDetail.coverImageUrl || RD_FALLBACK}
                            alt={'Cover of ' + (bdDetail.bookTitle || 'book')}
                            onError={(e) => rdCoverError(e, RD_FALLBACK)}
                          />
                        </div>
                        <div className="bookrow-info">
                          <b>{bdDetail.bookTitle || 'Book Discussion'}</b>
                          <span>by {bdDetail.author || 'Unknown'}</span>
                        </div>
                      </div>
                      <div className="book-specs">
                        <div className="spec-row">
                          <span className="spec-label">📖 Genre</span>
                          <span className="spec-val">{bdDetail.genre || '–'}</span>
                        </div>
                        <div className="spec-row">
                          <span className="spec-label">📄 Pages</span>
                          <span className="spec-val">{bdDetail.pageCount == null ? '–' : `${bdDetail.pageCount} pages`}</span>
                        </div>
                        <div className="spec-row">
                          <span className="spec-label">📅 Published</span>
                          <span className="spec-val">{bdDetail.publicationYear == null ? '–' : bdDetail.publicationYear}</span>
                        </div>
                      </div>
                      {bdDetail.keyQuote && (
                        <div className="quotebox">
                          <div className="quote-content">
                            <p className="quote-text">“{bdDetail.keyQuote}”</p>
                            <p className="quote-by">— {bdDetail.quoteAuthor || bdDetail.author || 'Author'}</p>
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
                            <span className="metric-label">💬 Discussions</span>
                          </div>
                          <div className="metric-value">{(bdDetail.threads || []).length}</div>
                        </div>
                        <div className="metric-box">
                          <div className="metric-top">
                            <span className="metric-label">👥 Members</span>
                          </div>
                          <div className="metric-value">{bdDetail.membersCount ?? 0}</div>
                        </div>
                      </div>
                      <div className="stats-caption">
                        <span className="live-dot" />
                        <span>People who joined this discussion</span>
                      </div>
                    </section>
                  </aside>
                </div>
              </div>
            )}
          </section>

          <section className={'view' + (view === 'recommend' ? ' on' : '')} id="v-recommend">
            <RecommendationsView />
          </section>

          <section className={'view' + (view === 'usershome' ? ' on' : '')} id="v-usershome">
            <div className="um-banner">
              <div className="um-banner-top">
                <div>
                  <h2>USER MANAGEMENT</h2>
                  <p>View and manage all users, organisers and members on the platform.</p>
                </div>
                <div className="um-read">
                  📚<br />
                  READ
                  <br />
                  REFLECT
                  <br />
                  REBUILD
                </div>
              </div>
              <div className="um-stats">
                <div className="um-stat">
                  <span className="ic" style={{ background: '#FBE9E4' }}>
                    👥
                  </span>
                  <div>
                    <small>Total Users</small>
                    <b>{uhSummary ? uhSummary.totalUsers : '–'}</b>
                  </div>
                </div>
                <div className="um-stat">
                  <span className="ic" style={{ background: '#E5F4E8' }}>
                    👤
                  </span>
                  <div>
                    <small>Members</small>
                    <b>{uhSummary ? uhSummary.totalReaders : '–'}</b>
                  </div>
                </div>
                <div className="um-stat">
                  <span className="ic" style={{ background: '#EDE9F7' }}>
                    👔
                  </span>
                  <div>
                    <small>Organisers</small>
                    <b>{uhSummary ? uhSummary.totalOrganizers : '–'}</b>
                  </div>
                </div>
                <div className="um-stat">
                  <span className="ic" style={{ background: '#FBE3E3' }}>
                    🚫
                  </span>
                  <div>
                    <small>Blocked Users</small>
                    <b>{uhSummary ? uhSummary.blockedUsers : '–'}</b>
                  </div>
                </div>
              </div>
            </div>
            <div className="um-panel">
              <div className="uh-tabs" role="tablist" aria-label="User groups">
                {(
                  [
                    ['all', 'All Users'],
                    ['org', 'Organisers'],
                    ['mem', 'Members'],
                  ] as Array<[UhTab, string]>
                ).map(([t, lbl]) => (
                  <button
                    key={t}
                    className="uh-tab"
                    data-tab={t}
                    aria-selected={uhTab === t ? 'true' : 'false'}
                    onClick={() => {
                      setUhTab(t);
                      setUhPage(0);
                      setUhSel([]);
                      setUhCheckAll(false);
                      if (t !== 'all') setUhRole('');
                    }}
                  >
                    {lbl} (
                    {t === 'all' ? (uhSummary ? uhSummary.totalUsers : '–') : t === 'org' ? (uhSummary ? uhSummary.totalOrganizers : '–') : uhSummary ? uhSummary.totalReaders : '–'})
                  </button>
                ))}
                <span className={'um-bulk' + (uhSel.length ? ' show' : '')}>
                  <b>{uhSel.length}</b>&nbsp;selected
                  <button className="um-block" type="button" onClick={() => void uhBulk(false)}>
                    Block
                  </button>
                  <button className="um-unblock" type="button" onClick={() => void uhBulk(true)}>
                    Unblock
                  </button>
                  <button
                    className="um-refresh"
                    type="button"
                    onClick={() => {
                      setUhSel([]);
                      setUhCheckAll(false);
                      void loadUh();
                    }}
                  >
                    Clear
                  </button>
                </span>
              </div>
              <div className="um-listhead" style={{ marginTop: 0 }}>
                <div className="um-tools" style={{ marginLeft: 0, flex: 1, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <input value={uhQInput} onChange={(e) => setUhQInput(e.target.value)} placeholder="🔍 Search users by name or email..." aria-label="Search users" style={{ flex: 1, minWidth: 200 }} />
                  <select value={uhRole} onChange={(e) => { setUhRole(e.target.value); setUhPage(0); }} disabled={uhTab !== 'all'} aria-label="Filter by role">
                    <option value="">All Roles</option>
                    <option value="ADMIN">Admin</option>
                    <option value="ORGANIZER">Organiser</option>
                    <option value="READER">Member</option>
                  </select>
                  <select value={uhStatus} onChange={(e) => { setUhStatus(e.target.value); setUhPage(0); }} aria-label="Filter by status">
                    <option value="">All Status</option>
                    <option value="active">Active</option>
                    <option value="inactive">Blocked</option>
                  </select>
                  <select value={uhSort} onChange={(e) => { setUhSort(e.target.value); setUhPage(0); }} aria-label="Sort users">
                    <option value="createdAt,desc">Joined Date (Newest)</option>
                    <option value="createdAt,asc">Joined Date (Oldest)</option>
                    <option value="name,asc">Name (A–Z)</option>
                  </select>
                  <button className="um-refresh" onClick={() => void uhExport()}>
                    ⬇ Export CSV
                  </button>
                </div>
              </div>
              <div style={{ overflow: 'auto' }}>
                <table className="um-table">
                  <thead>
                    <tr>
                      <th>
                        <input type="checkbox" checked={uhCheckAll} onChange={(e) => uhToggleAll(e.target.checked)} aria-label="Select page" />
                      </th>
                      <th>User</th>
                      <th>Email</th>
                      <th>Role</th>
                      <th>Joined Date</th>
                      <th>Status</th>
                      <th>Actions</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {uhRows.length ? (
                      uhRows.map((u) => {
                        const on = !!u.active;
                        const self = meId !== null && String(meId) === String(u.id);
                        return (
                          <Fragment key={u.id}>
                            <tr>
                              <td>
                                <input type="checkbox" className="uh-check" checked={uhSel.includes(u.id)} onChange={(e) => uhToggleOne(u.id, e.target.checked)} aria-label="Select user" />
                              </td>
                              <td>
                                <span className="um-av">
                                  {u.profilePictureUrl ? <img alt="" src={resolveMedia(u.profilePictureUrl)} /> : initialsOf(u.name || u.email)}
                                </span>{' '}
                                <b>{u.name || ''}</b>
                              </td>
                              <td>{u.email || ''}</td>
                              <td>{uhPill(u.role)}</td>
                              <td>{u.createdAt ? fmtDate(u.createdAt) : '–'}</td>
                              <td>
                                <span className={'st-pill ' + (on ? 'on' : 'off')}>● {on ? 'Active' : 'Blocked'}</span>
                              </td>
                              <td style={{ whiteSpace: 'nowrap' }}>
                                {!on ? (
                                  <button className="um-unblock" onClick={() => void uhSetActive(u.id, true)}>
                                    🔓 Unblock
                                  </button>
                                ) : u.role === 'ORGANIZER' ? (
                                  <button className="btn-demote" disabled={self} title={self ? 'You cannot demote yourself' : undefined} onClick={() => void uhDemote(u.id)}>
                                    ↓ Demote to Member
                                  </button>
                                ) : u.role === 'READER' ? (
                                  <>
                                    <button className="um-block" disabled={self} title={self ? 'You cannot block yourself' : undefined} onClick={() => void uhSetActive(u.id, false)}>
                                      🚫 Block
                                    </button>{' '}
                                    <button className="btn-promote" onClick={() => void uhPromote(u.id)}>
                                      ↑ Promote to Organiser
                                    </button>
                                  </>
                                ) : (
                                  <button className="um-block" disabled={self} title={self ? 'You cannot block yourself' : undefined} onClick={() => void uhSetActive(u.id, false)}>
                                    🚫 Block
                                  </button>
                                )}
                              </td>
                              <td style={{ position: 'relative' }}>
                                <button
                                  className="um-dots"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setUhMenu(uhMenu === u.id ? null : u.id);
                                  }}
                                >
                                  ⋯
                                </button>
                                {uhMenu === u.id && (
                                  <div className="um-menu">
                                    <button
                                      onClick={() => {
                                        try {
                                          void navigator.clipboard.writeText(u.email || '');
                                        } catch {
                                          /* ignore */
                                        }
                                        setUhMenu(null);
                                      }}
                                    >
                                      Copy email
                                    </button>
                                    <button
                                      onClick={() => {
                                        setUhMenu(null);
                                        setUhDetail(uhDetail === u.id ? null : u.id);
                                      }}
                                    >
                                      View details
                                    </button>
                                  </div>
                                )}
                              </td>
                            </tr>
                            {uhDetail === u.id && (
                              <tr className="um-xrow">
                                <td></td>
                                <td colSpan={7}>
                                  Name: <b>{u.name || ''}</b> · Email: {u.email || ''} · Role: {u.role === 'ADMIN' ? 'Admin' : u.role === 'ORGANIZER' ? 'Organiser' : 'Member'} · Joined:{' '}
                                  {u.createdAt ? fmtDate(u.createdAt) : '–'} · Status: {on ? 'Active' : 'Blocked'}
                                </td>
                              </tr>
                            )}
                          </Fragment>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', color: 'var(--muted)' }}>
                          No users found.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="um-pager">
                <span className="um-showing" style={{ margin: 0 }}>
                  Showing {uhTotal === 0 ? 0 : uhNumber * uhSize + 1} to {Math.min(uhTotal, (uhNumber + 1) * uhSize)} of {uhTotal} users.
                </span>
                <span style={{ flex: 1 }}></span>
                <button className="um-pg" disabled={uhNumber === 0} onClick={() => { if (uhNumber > 0) setUhPage(uhNumber - 1); }}>
                  ‹
                </button>
                {uhPageNums.map((i) => (
                  <button key={i} className="um-pg" aria-current={i === uhNumber ? 'page' : 'false'} onClick={() => setUhPage(i)}>
                    {i + 1}
                  </button>
                ))}
                <button className="um-pg" disabled={uhNumber >= uhPages - 1} onClick={() => { if (uhNumber < uhPages - 1) setUhPage(uhNumber + 1); }}>
                  ›
                </button>
              </div>
            </div>
          </section>

          <section className={'view' + (view === 'analytics' ? ' on' : '')} id="v-analytics">
            <h2>Analytics</h2>
            <p className="sub">
              Live from <code>GET /api/admin/analytics</code>.
            </p>
            <div>
              <table>
                <tr>
                  <th>Circle</th>
                  <th>Members</th>
                  <th>Events</th>
                  <th>Activity</th>
                </tr>
                {ana.map((x, i) => (
                  <tr key={i}>
                    <td>{x.name}</td>
                    <td>{x.membersCount}</td>
                    <td>{x.eventsCount}</td>
                    <td>{x.activityRate}%</td>
                  </tr>
                ))}
              </table>
            </div>
          </section>

          <section className={'view' + (view === 'books' ? ' on' : '')} id="v-books">
            {view === 'books' && <AdminBooks />}
          </section>

          <section className={'view' + (view === 'orders' ? ' on' : '')} id="v-orders">
            {view === 'orders' && <AdminOrders />}
          </section>

          <section className={'view' + (view === 'settings' ? ' on' : '')} id="v-settings">
            <div className="set-wrap">
              <h2 style={{ fontFamily: "'Fraunces',serif", fontSize: 28, margin: '0 0 4px', color: 'var(--burgundy)' }}>Settings</h2>
              <p className="sub" style={{ margin: '0 0 20px', color: '#7A6F62', fontSize: 14 }}>Manage your account and notification preferences.</p>
              <div className="set-card">
                <div className="set-head">
                  <span className="set-ic">👤</span>
                  <div>
                    <h3>Admin Profile</h3>
                    <p>Manage your admin account details.</p>
                  </div>
                </div>
                <div className="set-profile">
                  <div className="set-photo">
                    <div className="set-bigav">
                      {setAvUrl ? <img alt="" src={resolveMedia(setAvUrl)} /> : initialsOf(setName || 'A')}
                    </div>
                    <label className="set-photo-btn" style={{ display: 'inline-block', cursor: 'pointer' }}>
                      📷 Change Photo
                      <input type="file" accept="image/*" hidden onChange={onPhotoFile} />
                    </label>
                    <div className="set-saved">{setSavedTick}</div>
                  </div>
                  <div className="set-form">
                    <div className="set-form-row">
                      <label htmlFor="setName">Name</label>
                      <input id="setName" value={setName} onChange={(e) => setSetName(e.target.value)} onBlur={() => void saveProfile()} autoComplete="name" />
                    </div>
                    <div className="set-form-row">
                      <label htmlFor="setEmail">Email</label>
                      <input id="setEmail" value={setEmail} onChange={(e) => setSetEmail(e.target.value)} onBlur={() => void saveProfile()} autoComplete="email" />
                    </div>
                    <div className="set-form-row">
                      <label htmlFor="setRole">Role</label>
                      <input id="setRole" value={setRole} disabled />
                    </div>
                  </div>
                </div>
              </div>
              <div className="set-card">
                <div className="set-head">
                  <span className="set-ic">🔔</span>
                  <div>
                    <h3>Notification Settings</h3>
                    <p>Choose what notifications you want to receive.</p>
                  </div>
                </div>
                <div>
                  {SET_TOGGLES.map(([key, title, desc]) => (
                    <div className="set-row" key={key}>
                      <div className="grow">
                        <b>{title}</b>
                        <small>{desc}</small>
                      </div>
                      <label className="tgl">
                        <input type="checkbox" checked={prefs[key] !== false} onChange={(e) => void togglePref(key, e.target.checked)} aria-label={title} />
                        <i></i>
                      </label>
                    </div>
                  ))}
                </div>
              </div>
              <div className="set-card">
                <div className="set-head">
                  <span className="set-ic">🛡️</span>
                  <div>
                    <h3>Security Settings</h3>
                    <p>Manage your account security.</p>
                  </div>
                </div>
                <div className="set-row">
                  <div className="grow">
                    <b>Change password</b>
                    <small>Update your admin account password.</small>
                  </div>
                  <button className="set-btn" onClick={() => { setPwErr(''); setPwOpen(true); }}>
                    🔒 Change Password
                  </button>
                </div>
                <div className="set-row">
                  <div className="grow">
                    <b>Sign out of all devices</b>
                    <small>Sign out from all devices.</small>
                  </div>
                  <button className="set-btn solid" onClick={() => setAllOpen(true)}>
                    ⎋ Sign Out All
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
      <>
        {!blockTarget ? null : (
          <div className="um-veil" onClick={(e) => { if (e.target === e.currentTarget) setBlockTarget(null); }}>
            <div className="um-modal" role="dialog" aria-label="Block user confirmation">
              <div className="um-modal-top">
                <span className="um-modal-ic">🚫</span>
                <h2>Block User?</h2>
                <button className="um-modal-x" type="button" aria-label="Close" onClick={() => setBlockTarget(null)}>
                  ✕
                </button>
              </div>
              <p style={{ fontSize: 13.5, margin: '14px 0 0' }}>
                Are you sure you want to block <b>{(blockTarget.name || blockTarget.email || 'this user') + '?'}</b>
              </p>
              <div className="um-modal-user">
                <span className="um-av" style={{ width: 38, height: 38, fontSize: 15 }}>
                  {initialsOf(blockTarget.name || blockTarget.email)}
                </span>
                <div>
                  <b>{blockTarget.name || blockTarget.email}</b>
                  <span>{blockTarget.email || ''}</span>
                </div>
              </div>
              <div className="um-conseq">
                <b>A blocked user will:</b>
                <ul>
                  <li>Not be able to log in</li>
                  <li>Not be able to join circles</li>
                  <li>Not be able to participate in events</li>
                  <li>Their active sessions will be terminated</li>
                </ul>
              </div>
              <label htmlFor="umBlockReason">Reason (optional)</label>
              <textarea value={blockReason} onChange={(e) => setBlockReason(e.target.value)} placeholder="Enter reason for blocking..." />
              <div className="um-modal-btns">
                <button className="btn ghost" type="button" style={{ flex: 1 }} onClick={() => setBlockTarget(null)}>
                  Cancel
                </button>
                <button className="btn" type="button" style={{ flex: 1 }} onClick={() => void uhConfirmBlock()}>
                  🚫 Block User
                </button>
              </div>
            </div>
          </div>
        )}
        {!pwOpen ? null : (
          <div className="um-veil" onClick={(e) => { if (e.target === e.currentTarget) setPwOpen(false); }}>
            <div className="um-modal" role="dialog" aria-label="Settings dialog">
              <div className="um-modal-top">
                <span className="um-modal-ic">🔒</span>
                <h2>Change Password</h2>
              </div>
              <div className="set-form" style={{ marginTop: 12 }}>
                {pwErr && <div className="err">{pwErr}</div>}
                <label htmlFor="setPwCur">Current password</label>
                <input id="setPwCur" type="password" value={pwCur} onChange={(e) => setPwCur(e.target.value)} autoComplete="current-password" />
                <label htmlFor="setPwNew">New password (min 8 characters)</label>
                <input id="setPwNew" type="password" value={pwNew} onChange={(e) => setPwNew(e.target.value)} autoComplete="new-password" />
              </div>
              <div className="set-modal-btns">
                <button className="btn ghost" type="button" onClick={() => setPwOpen(false)}>
                  Cancel
                </button>
                <button className="btn" type="button" onClick={() => void changePassword()}>
                  Update Password
                </button>
              </div>
            </div>
          </div>
        )}
        {!allOpen ? null : (
          <div className="um-veil" onClick={(e) => { if (e.target === e.currentTarget) setAllOpen(false); }}>
            <div className="um-modal" role="dialog" aria-label="Settings dialog">
              <div className="um-modal-top">
                <span className="um-modal-ic">⎋</span>
                <h2>Sign Out All?</h2>
              </div>
              <p style={{ fontSize: 13 }}>This ends every login session on all devices, including this one. Continue?</p>
              <div className="set-modal-btns">
                <button className="btn ghost" type="button" onClick={() => setAllOpen(false)}>
                  Cancel
                </button>
                <button className="btn" type="button" onClick={() => void signOutAll()}>
                  Sign Out All
                </button>
              </div>
            </div>
          </div>
        )}
      </>
    </>
  );
}
