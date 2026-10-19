import { useEffect, useMemo, useState } from 'react';
import { Link, Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, fmtDate, fmtTime, initialsOf, resolveMedia, timeAgo } from '../lib/api';
import ReaderLayout from '../components/ReaderLayout';
import { ManageArea } from './CircleManage';
import './ReaderCircles.css';
import './CircleManage.css';

import { COVER_PLACEHOLDER as FALLBACK } from '../lib/placeholders';

const BANNER_FALLBACK =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="380" viewBox="0 0 1200 380">' +
      '<defs>' +
      '<linearGradient id="bgb" x1="0%" y1="0%" x2="100%" y2="100%">' +
      '<stop offset="0%" stop-color="#3B170F"/>' +
      '<stop offset="45%" stop-color="#5B2318"/>' +
      '<stop offset="100%" stop-color="#240D07"/>' +
      '</linearGradient>' +
      '<radialGradient id="glow" cx="80%" cy="30%" r="55%">' +
      '<stop offset="0%" stop-color="#D8B168" stop-opacity="0.25"/>' +
      '<stop offset="100%" stop-color="#240D07" stop-opacity="0"/>' +
      '</radialGradient>' +
      '<pattern id="pat" width="40" height="40" patternUnits="userSpaceOnUse">' +
      '<circle cx="20" cy="20" r="1" fill="#D8B168" opacity="0.18"/>' +
      '</pattern>' +
      '</defs>' +
      '<rect width="1200" height="380" fill="url(#bgb)"/>' +
      '<rect width="1200" height="380" fill="url(#pat)"/>' +
      '<rect width="1200" height="380" fill="url(#glow)"/>' +
      '<g opacity="0.15" stroke="#D8B168" stroke-width="1.5" fill="none">' +
      '<circle cx="980" cy="190" r="140"/>' +
      '<circle cx="980" cy="190" r="90"/>' +
      '<path d="M840 190 H1120 M980 50 V330"/>' +
      '</g>' +
      '<g opacity="0.12" fill="#FAF2E6">' +
      '<path d="M120 120 C150 100 190 100 220 120 V190 C190 170 150 170 120 190 Z"/>' +
      '<path d="M220 120 C250 100 290 100 320 120 V190 C290 170 250 170 220 190 Z"/>' +
      '</g>' +
    '</svg>',
  );

type Circle = {
  id: string;
  name: string;
  description?: string;
  memberCount?: number;
  coverImageUrl?: string;
  category?: string;
  tags?: string[];
  createdAt?: string;
};

type Membership = {
  id: string;
  circleId: string;
  status: string;
  circleName?: string;
  circleCoverImageUrl?: string;
  requestedAt?: string;
  decidedAt?: string;
};

type CircleEvent = {
  id: string;
  circleId?: string;
  title?: string;
  description?: string;
  readingTopic?: string;
  coverImageUrl?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  registeredCount?: number;
  capacity?: number;
  isRegistered?: boolean;
  status?: string;
};

type Organizer = {
  userId?: string;
  name?: string;
  avatarUrl?: string;
  city?: string;
  bio?: string;
  interests?: string[];
  joinedAt?: string;
  addedAt?: string;
};

type OrganizersResp = {
  current?: Organizer[];
  past?: Organizer[];
  currentCount?: number;
  pastCount?: number;
};

type MemberStats = {
  totalMembers?: number;
  pendingRequests?: number;
  regularMembers?: number;
};

type CircleMember = {
  readerId?: string;
  readerName?: string;
  avatarUrl?: string;
  city?: string;
  bio?: string;
  interests?: string[];
  requestedAt?: string;
};

type Overview = {
  counts?: { upcomingEvents?: number };
};


function tagsOf(c: Circle): string[] {
  if (c.tags && c.tags.length) return c.tags.slice(0, 4);
  if (c.category) return [c.category];
  return [];
}

function statusMap(mine: Membership[]): Record<string, { status: string; id: string }> {
  const m: Record<string, { status: string; id: string }> = {};
  (mine || []).forEach((x) => {
    m[x.circleId] = { status: x.status, id: x.id };
  });
  return m;
}

async function upcomingCounts(): Promise<Record<string, number>> {
  let ev: CircleEvent[] = [];
  try {
    ev = await api<CircleEvent[]>('/api/events');
  } catch {
    ev = [];
  }
  const map: Record<string, number> = {};
  ev.forEach((e) => {
    if (String(e.status || '').toUpperCase() === 'PUBLISHED' && e.circleId) {
      map[e.circleId] = (map[e.circleId] || 0) + 1;
    }
  });
  return map;
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function CircleTabs({ active, pend }: { active: 'my' | 'explore' | 'requests'; pend: number }) {
  const nav = useNavigate();
  return (
    <div className="tabs" role="tablist">
      <button role="tab" className={active === 'my' ? 'on' : ''} onClick={() => nav('/reader-circles')}>
        My Circles
      </button>
      <button role="tab" className={active === 'explore' ? 'on' : ''} onClick={() => nav('/reader-circles/explore')}>
        Explore Circles
      </button>
      <button role="tab" className={active === 'requests' ? 'on' : ''} onClick={() => nav('/reader-circles/requests')}>
        Join Requests{pend ? <span className="cbadge">{pend}</span> : null}
      </button>
    </div>
  );
}

function CircleCard({
  c,
  up,
  st,
  busy,
  onJoin,
  onOpen,
  isOrg,
  onManage,
}: {
  c: Circle;
  up?: number;
  st?: { status: string; id: string };
  busy: boolean;
  onJoin: (id: string) => void;
  onOpen: (id: string) => void;
  isOrg?: boolean;
  onManage?: (id: string) => void;
}) {
  let action: React.ReactNode;
  if (isOrg) {
    action = (
      <button className="btn" onClick={() => onManage ? onManage(c.id) : onOpen(c.id)}>
        Manage Circle →
      </button>
    );
  } else if (!st) {
    action = (
      <button className="cardbtn" disabled={busy} onClick={() => onJoin(c.id)}>
        ➕ Request to Join
      </button>
    );
  } else if (st.status === 'PENDING') {
    action = <button className="cardbtn" disabled>Requested — pending</button>;
  } else if (st.status === 'APPROVED') {
    action = (
      <button className="cardbtn solid" onClick={() => onOpen(c.id)}>
        Open Circle →
      </button>
    );
  } else {
    action = (
      <button className="cardbtn" disabled={busy} onClick={() => onJoin(c.id)}>
        Request again
      </button>
    );
  }
  return (
    <div className="ccard">
      <img
        className="cover"
        src={resolveMedia(c.coverImageUrl) || FALLBACK}
        alt={'Cover of ' + (c.name || 'circle')}
        onError={(e) => {
          e.currentTarget.onerror = null;
          e.currentTarget.src = FALLBACK;
        }}
      />
      <div className="cb">
        <h4>{c.name}</h4>
        {isOrg ? <span className="pill org">👑 ORGANIZER</span> : null}
        <div className="meta">
          <span>👥 {c.memberCount} members</span>
          <span>📅 {up || 0} upcoming events</span>
        </div>
        <p className="desc">{c.description || ''}</p>
        <div className="tagrow">
          {tagsOf(c).map((t, i) => (
            <span key={i}>{t}</span>
          ))}
        </div>
        {action}
      </div>
    </div>
  );
}

function MyView({ setSync }: { setSync: (s: string) => void }) {
  const nav = useNavigate();
  const [mine, setMine] = useState<Membership[]>([]);
  const [circles, setCircles] = useState<Circle[]>([]);
  const [up, setUp] = useState<Record<string, number>>({});
  const [orgIds, setOrgIds] = useState<Record<string, boolean>>({});
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');

  useEffect(() => {
    let on = true;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const m = await api<Membership[]>('/api/users/me/memberships');
        const cs = await api<Circle[]>('/api/circles?sort=name_asc');
        const map = await upcomingCounts();
        let org: { id?: string }[] = [];
        try {
          org = await api<{ id?: string }[]>('/api/users/me/organized-circles');
        } catch {
          org = [];
        }
        if (!on) return;
        setMine(m || []);
        setCircles(cs || []);
        setUp(map);
        const o: Record<string, boolean> = {};
        (org || []).forEach((x) => { if (x.id) o[x.id] = true; });
        setOrgIds(o);
        setErr('');
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [tick, setSync]);

  const sm = useMemo(() => statusMap(mine), [mine]);
  const approved = useMemo(
    () => circles.filter((c) => (sm[c.id] && sm[c.id].status === 'APPROVED') || orgIds[c.id]),
    [circles, sm, orgIds],
  );
  const pending = useMemo(() => mine.filter((x) => x.status === 'PENDING'), [mine]);

  async function join(id: string) {
    setBusyId(id);
    try {
      await api('/api/circles/' + id + '/join-requests', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  function open(id: string) {
    nav('/reader-circles/circle/' + id);
  }

  if (err) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="err">{err}</div>
        </main>
        <aside />
      </div>
    );
  }

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <h1 className="page">Circles</h1>
        <p className="sub">Discover reading circles, join communities, and be part of meaningful discussions.</p>
        <CircleTabs active="my" pend={pending.length} />
        <div className="secttl">
          <h2>My Circles</h2>
          <span className="countpill">{approved.length}</span>
        </div>
        <div className="grid3">
          {approved.length ? (
            approved.map((c) => (
              <CircleCard
                key={c.id}
                c={c}
                up={up[c.id]}
                st={sm[c.id]}
                busy={busyId === c.id}
                onJoin={join}
                onOpen={open}
                isOrg={!!orgIds[c.id]}
                onManage={(cid) => nav('/reader-circles/manage/' + cid)}
              />
            ))
          ) : (
            <div className="empty">No circles yet — explore and send your first join request.</div>
          )}
        </div>
      </main>
      <aside>
        <div className="roles-panel">
          <h3>👥 My Circle Roles</h3>
          <p className="sub">Your role in each circle you are part of.</p>
          {approved.length ? (
            approved.map((c) => (
              <div className="rrow" key={c.id}>
                {c.coverImageUrl ? (
                  <img src={resolveMedia(c.coverImageUrl)} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                ) : (
                  <span className="face">{initialsOf(c.name)}</span>
                )}
                <div>
                  <b>{c.name}</b>
                  <small>👥 {c.memberCount ?? '–'} member{(c.memberCount ?? 0) === 1 ? '' : 's'}</small>
                </div>
                {orgIds[c.id] ? <span className="pill org">ORGANIZER</span> : <span className="pill reader">READER</span>}
              </div>
            ))
          ) : (
            <p className="sub">No circles yet.</p>
          )}
        </div>
      </aside>
    </div>
  );
}

function ExploreView({ externalQ, setSync }: { externalQ: string; setSync: (s: string) => void }) {
  const nav = useNavigate();
  const [mine, setMine] = useState<Membership[]>([]);
  const [ups, setUps] = useState<Record<string, number>>({});
  const [list, setList] = useState<Circle[]>([]);
  const [sort, setSort] = useState('members_desc');
  const [q, setQ] = useState('');
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');

  useEffect(() => {
    setQ(externalQ || '');
  }, [externalQ]);

  useEffect(() => {
    let on = true;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const m = await api<Membership[]>('/api/users/me/memberships');
        const map = await upcomingCounts();
        if (!on) return;
        setMine(m || []);
        setUps(map);
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [tick, setSync]);

  useEffect(() => {
    let on = true;
    const t = window.setTimeout(() => {
      (async () => {
        try {
          let p = '?sort=' + encodeURIComponent(sort || 'members_desc');
          if (q) p += '&search=' + encodeURIComponent(q);
          const data = await api<Circle[]>('/api/circles' + p);
          if (!on) return;
          setList(data || []);
          setErr('');
        } catch (e) {
          if (!on) return;
          setErr(errMsg(e));
        }
      })();
    }, 300);
    return () => {
      on = false;
      window.clearTimeout(t);
    };
  }, [sort, q]);

  const sm = useMemo(() => statusMap(mine), [mine]);
  const pend = mine.filter((x) => x.status === 'PENDING').length;
  const appr = mine.filter((x) => x.status === 'APPROVED').length;
  const rej = mine.filter((x) => x.status === 'REJECTED').length;

  async function join(id: string) {
    setBusyId(id);
    try {
      await api('/api/circles/' + id + '/join-requests', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  function open(id: string) {
    nav('/reader-circles/circle/' + id);
  }

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <h1 className="page">Circles</h1>
        <p className="sub">Discover reading circles, join communities, and be part of meaningful discussions.</p>
        <CircleTabs active="explore" pend={pend} />
        <div className="toolbar">
          <div className="search" style={{ flex: 1, minWidth: 200 }}>
            <span className="lens">🔍</span>
            <input type="search" placeholder="Search circles by name, genre, topic…" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <label style={{ fontSize: 13, color: 'var(--muted)' }}>Sort by</label>
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="members_desc">Most Members</option>
            <option value="name_asc">Name A–Z</option>
            <option value="newest">Newest</option>
          </select>
        </div>
        {err ? (
          <div className="err">{err}</div>
        ) : (
          <div className="grid3">
            {list.length ? (
              list.map((c) => (
                <CircleCard key={c.id} c={c} up={ups[c.id]} st={sm[c.id]} busy={busyId === c.id} onJoin={join} onOpen={open} />
              ))
            ) : (
              <div className="empty">No circles match your search.</div>
            )}
          </div>
        )}
      </main>
      <aside aria-label="Details">
        <section className="panel">
          <h3>👥 My Membership Status</h3>
          <div className="statline">
            📩 {pend} Pending Requests <Link to="/reader-circles/requests">›</Link>
          </div>
          <div className="statline">
            📚 {appr} Joined Circles <Link to="/reader-circles">›</Link>
          </div>
          <div className="statline">
            ⓘ {rej} Declined Requests <Link to="/reader-circles/requests">›</Link>
          </div>
        </section>
      </aside>
    </div>
  );
}

function RequestsView({ setSync }: { setSync: (s: string) => void }) {
  const nav = useNavigate();
  const [mine, setMine] = useState<Membership[]>([]);
  const [circles, setCircles] = useState<Circle[]>([]);
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');

  useEffect(() => {
    let on = true;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const m = await api<Membership[]>('/api/users/me/memberships');
        const cs = await api<Circle[]>('/api/circles?sort=name_asc');
        if (!on) return;
        setMine(m || []);
        setCircles(cs || []);
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [tick, setSync]);

  const byId = useMemo(() => {
    const m: Record<string, Circle> = {};
    circles.forEach((c) => {
      m[c.id] = c;
    });
    return m;
  }, [circles]);

  const pend = mine.filter((x) => x.status === 'PENDING');
  const acc = mine.filter((x) => x.status === 'APPROVED');
  const dec = mine.filter((x) => x.status === 'REJECTED');

  async function cancel(id: string) {
    setBusyId(id);
    try {
      await api('/api/join-requests/' + id, { method: 'DELETE' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  function open(id: string) {
    nav('/reader-circles/circle/' + id);
  }

  function friendlyWhen(iso?: string): string {
    if (!iso) return '';
    const t = new Date(iso).getTime();
    if (isNaN(t)) return '';
    const days = Math.floor((Date.now() - t) / 86400000);
    if (days <= 0) return timeAgo(iso);
    if (days === 1) return 'a day ago';
    if (days < 7) return days + ' days ago';
    if (days < 14) return 'a week ago';
    if (days < 30) return Math.floor(days / 7) + ' weeks ago';
    return timeAgo(iso);
  }

  function row(x: Membership, st: string) {
    const c = byId[x.circleId] || ({} as Circle);
    const cover = resolveMedia(c.coverImageUrl) || resolveMedia(x.circleCoverImageUrl) || FALLBACK;
    const members = c.memberCount != null ? c.memberCount + ' members' : '';
    if (st === 'APPROVED') {
      return (
        <div className="bigrow rq-acc" key={x.id}>
          <img
            src={cover}
            alt=""
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = FALLBACK;
            }}
          />
          <div className="rq-info">
            <button className="rq-title" onClick={() => open(x.circleId)}>
              {x.circleName}
            </button>
            <p>{c.description || 'Welcome to the circle! Connect with fellow members and join upcoming discussions.'}</p>
            <div className="mrow">
              {members && <span>👥 {members}</span>}
              <span>📅 Accepted {friendlyWhen(x.decidedAt)}</span>
            </div>
          </div>
          <div className="rq-side">
            <span className="statuspill accepted">✔ Accepted</span>
            <button className="ghostbtn sm" onClick={() => open(x.circleId)}>
              Open Circle →
            </button>
          </div>
        </div>
      );
    }
    if (st === 'REJECTED') {
      return (
        <div className="rq-dec" key={x.id}>
          <span className="rq-x" aria-hidden="true">
            ✕
          </span>
          <span className="rq-dec-tx">
            <b>{x.circleName}</b>
            <span>Declined {friendlyWhen(x.decidedAt)}</span>
          </span>
          <button className="ghostbtn sm" onClick={() => open(x.circleId)}>
            View Circle ›
          </button>
        </div>
      );
    }
    return (
      <div className="bigrow" key={x.id}>
        <img
          src={cover}
          alt=""
          onError={(e) => {
            e.currentTarget.onerror = null;
            e.currentTarget.src = FALLBACK;
          }}
        />
        <div className="rq-info">
          <button className="rq-title" onClick={() => open(x.circleId)}>
            {x.circleName}
          </button>
          <p>{c.description || ''}</p>
          <div className="mrow">
            {members && <span>👥 {members}</span>}
            <span>📅 Requested {timeAgo(x.requestedAt)}</span>
          </div>
        </div>
        <div className="rq-side">
          <span className="statuspill pending">🕐 Pending</span>
          <button className="ghostbtn sm danger" disabled={busyId === x.id} onClick={() => cancel(x.id)}>
            Cancel Request
          </button>
        </div>
      </div>
    );
  }

  if (err) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="err">{err}</div>
        </main>
        <aside />
      </div>
    );
  }

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <h1 className="page">Circles</h1>
        <p className="sub">Discover reading circles, join communities, and be part of meaningful discussions.</p>
        <CircleTabs active="requests" pend={pend.length} />
        <div className="secttl">
          <h2>Pending Requests</h2>
          <span className="countpill">{pend.length}</span>
        </div>
        <p className="sub">These are circles you have requested to join.</p>
        {pend.length ? (
          pend.map((x) => row(x, 'PENDING'))
        ) : (
          <div className="rq-empty">
            <svg viewBox="0 0 120 96" width="132" height="106" aria-hidden="true">
              <ellipse cx="60" cy="86" rx="42" ry="7" fill="#F3ECDD" />
              <rect x="34" y="14" width="52" height="62" rx="9" fill="#F6EDD9" />
              <rect x="43" y="28" width="34" height="5" rx="2.5" fill="#D9C6A5" />
              <rect x="43" y="38" width="34" height="5" rx="2.5" fill="#E4D5B8" />
              <rect x="43" y="48" width="22" height="5" rx="2.5" fill="#E4D5B8" />
              <circle cx="82" cy="62" r="14" fill="#C97B5A" />
              <path d="M82 55v7l5 3" stroke="#FFF8F0" strokeWidth="3" strokeLinecap="round" fill="none" />
              <path d="M96 12l4-6M103 20l6-2" stroke="#C97B5A" strokeWidth="2.5" strokeLinecap="round" />
            </svg>
            <b>No pending requests</b>
            <span>You haven&rsquo;t requested to join any circles.</span>
          </div>
        )}
        <div className="secttl">
          <h2>Accepted Requests</h2>
          <span className="countpill">{acc.length}</span>
        </div>
        <p className="sub">You are now a member of these circles.</p>
        {acc.length ? acc.map((x) => row(x, 'APPROVED')) : <div className="empty">No accepted requests yet.</div>}
        <div className="secttl">
          <h2>Declined Requests</h2>
          <span className="countpill">{dec.length}</span>
        </div>
        <p className="sub">These requests were declined by the organizers.</p>
        {dec.length ? (
          dec.map((x) => row(x, 'REJECTED'))
        ) : (
          <div className="rq-dec static">
            <span className="rq-x" aria-hidden="true">
              ✕
            </span>
            <span className="rq-dec-tx">
              <b>No declined requests</b>
              <span>You don&rsquo;t have any declined requests.</span>
            </span>
            <span className="rq-chev" aria-hidden="true">
              ›
            </span>
          </div>
        )}
      </main>
      <aside aria-label="Details">
        <section className="panel">
          <h3>📊 Request Summary</h3>
          <div className="statline">
            <span className="sic pend" aria-hidden="true">
              ◷
            </span>{' '}
            {pend.length} Pending Requests
          </div>
          <div className="statline">
            <span className="sic ok" aria-hidden="true">
              ✔
            </span>{' '}
            {acc.length} Accepted (Now a Member)
          </div>
          <div className="statline">
            <span className="sic no" aria-hidden="true">
              ✖
            </span>{' '}
            {dec.length} Declined Requests
          </div>
        </section>
        <section className="panel howto">
          <h3>💡 How it works?</h3>
          <ol>
            <li>
              <span className="n">1</span> Explore circles you&rsquo;re interested in.
            </li>
            <li>
              <span className="n">2</span> Send a join request.
            </li>
            <li>
              <span className="n">3</span> Wait for the organizer to approve.
            </li>
            <li>
              <span className="n">4</span> Once approved, you&rsquo;ll become a member and can access the
              circle&rsquo;s discussions, events and more.
            </li>
          </ol>
        </section>
      </aside>
    </div>
  );
}

function AboutRail({ c }: { c: Circle }) {
  return (
    <section className="panel">
      <h3>📖 About this circle</h3>
      <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>{c.description || ''}</p>
      <ul className="aboutlist">
        <li>📅 Created on {fmtDate(c.createdAt)}</li>
        <li>🔒 Private circle (Join request required)</li>
        <li>🏷 Focus: {c.category || 'General'}</li>
      </ul>
    </section>
  );
}

/* Section tabs for the reader circle detail area: Overview / Events /
   Members — same three tabs as the organiser manage screen, minus Join
   Requests (organisers/moderation only). */
function DetailTabs({ id, active }: { id: string; active: 'overview' | 'events' | 'members' }) {
  const nav = useNavigate();
  const tabs = [
    { k: 'overview', label: '🏠 Overview', to: '/reader-circles/circle/' + id },
    { k: 'events', label: '📅 Events', to: '/reader-circles/circle/' + id + '/events' },
    { k: 'members', label: '👥 Members', to: '/reader-circles/circle/' + id + '/members' },
  ] as const;
  return (
    <div className="mg-tabs" role="tablist" aria-label="Circle sections">
      {tabs.map((t) => (
        <button
          key={t.k}
          type="button"
          role="tab"
          aria-selected={active === t.k}
          className={active === t.k ? 'on' : ''}
          onClick={() => nav(t.to)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

function DetailView({ setSync, initTab }: { setSync: (s: string) => void; initTab?: 'overview' | 'events' | 'members' }) {
  const tab = initTab || 'overview';
  const { id } = useParams<{ id: string }>();
  const [circle, setCircle] = useState<Circle | null>(null);
  const [mine, setMine] = useState<Membership[]>([]);
  const [ev, setEv] = useState<CircleEvent[]>([]);
  const [orgPrev, setOrgPrev] = useState<Organizer[]>([]);
  const [orgCount, setOrgCount] = useState(0);
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let on = true;
    if (!id) return;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const c = await api<Circle>('/api/circles/' + id);
        const m = await api<Membership[]>('/api/users/me/memberships');
        let events: CircleEvent[] = [];
        try {
          events = await api<CircleEvent[]>('/api/circles/' + id + '/events?scope=upcoming');
        } catch {
          events = [];
        }
        let orgs: OrganizersResp | null = null;
        try {
          orgs = await api<OrganizersResp>('/api/circles/' + id + '/organizers');
        } catch {
          orgs = null;
        }
        if (!on) return;
        setCircle(c);
        setMine(m || []);
        setEv(events || []);
        setOrgPrev((orgs && orgs.current) || []);
        setOrgCount(Number((orgs && orgs.current && orgs.current.length) || 0));
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [id, tick, setSync]);

  const sm = useMemo(() => statusMap(mine), [mine]);
  const st = id ? sm[id] : undefined;

  async function joinCircle() {
    if (!id) return;
    try {
      await api('/api/circles/' + id + '/join-requests', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    }
  }

  if (err) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="err">{err}</div>
        </main>
        <aside />
      </div>
    );
  }
  if (!circle) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="empty">Loading circle…</div>
        </main>
        <aside />
      </div>
    );
  }

  const badge = !st ? (
    <span className="statuspill pending">Not a member</span>
  ) : st.status === 'APPROVED' ? (
    <span className="memberpill">✔ Member</span>
  ) : st.status === 'PENDING' ? (
    <span className="statuspill pending">Requested</span>
  ) : (
    <span className="statuspill declined">Declined</span>
  );

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <Link className="backlink" to="/reader-circles">
          ← Back to Circles
        </Link>
        <div className="rc-circle-hero-card">
          <div className="rc-circle-cover-wrap">
            <img
              className="rc-circle-cover-img"
              src={resolveMedia(circle.coverImageUrl) || BANNER_FALLBACK}
              alt={circle.name}
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = BANNER_FALLBACK;
              }}
            />
            <div className="rc-circle-cover-overlay" />
          </div>
          <div className="rc-circle-profile-bar">
            <div className="rc-circle-avatar-shell">
              {circle.coverImageUrl ? (
                <img
                  className="rc-circle-avatar-img"
                  src={resolveMedia(circle.coverImageUrl)}
                  alt={circle.name}
                  onError={(e) => {
                    e.currentTarget.onerror = null;
                    e.currentTarget.src = FALLBACK;
                  }}
                />
              ) : (
                <div className="rc-circle-avatar-monogram">
                  <span className="rc-circle-avatar-icon">📖</span>
                  <span className="rc-circle-avatar-initials">{initialsOf(circle.name || 'RC')}</span>
                </div>
              )}
            </div>
            <div className="rc-circle-profile-info">
              <div className="rc-circle-title-row">
                <div className="rc-circle-title-col">
                  <h1 className="rc-circle-name">{circle.name}</h1>
                  {circle.description && <p className="rc-circle-description">{circle.description}</p>}
                </div>
                <div className="rc-circle-badge-box">
                  {badge}
                  {(!st || st.status === 'DECLINED') && (
                    <button
                      className="cardbtn solid"
                      style={{ padding: '8px 16px', fontSize: 12.5, marginLeft: 10, whiteSpace: 'nowrap' }}
                      onClick={() => void joinCircle()}
                    >
                      {st?.status === 'DECLINED' ? 'Request Again' : '🔒 Join Circle'}
                    </button>
                  )}
                </div>
              </div>
              <div className="rc-circle-meta-row">
                <span className="rc-meta-chip">
                  👥 <b>{circle.memberCount} members</b>
                </span>
                <span className="rc-meta-chip">
                  📅 <b>{ev.length} upcoming events</b>
                </span>
                {circle.category && (
                  <span className="rc-meta-chip">
                    🏷️ <b>{circle.category}</b>
                  </span>
                )}
              </div>
              {tagsOf(circle).length > 0 && (
                <div className="rc-circle-tags-row">
                  {tagsOf(circle).map((t, i) => (
                    <span key={i} className="rc-circle-tag-pill">{t}</span>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
        {circle.id && <DetailTabs id={circle.id} active={tab} />}
        {tab === 'overview' ? (
        <>
        <div className="card">
          <div className="headrow">
            <h3>🧺 Circle Information</h3>
          </div>
          <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
            <div className="coverbox">
              <img
                src={resolveMedia(circle.coverImageUrl) || FALLBACK}
                alt=""
                onError={(e) => {
                  e.currentTarget.onerror = null;
                  e.currentTarget.src = FALLBACK;
                }}
              />
              <div className="cap">Cover Image</div>
            </div>
            <div style={{ flex: '1 1 220px', minWidth: 0 }}>
              <div className="kv"><b>Circle Name</b><span>{circle.name}</span></div>
              <div className="kv"><b>Description</b><span>{circle.description || '—'}</span></div>
              <div className="kv"><b>Tags</b>
                {tagsOf(circle).length ? (
                  <span className="tagrow">{tagsOf(circle).map((t, i) => <span key={i}>{t}</span>)}</span>
                ) : (
                  <span>—</span>
                )}
              </div>
            </div>
          </div>
        </div>
        </>
        ) : tab === 'events' ? (
          id ? <CircleEventsPanel circleId={id} setSync={setSync} /> : null
        ) : (
          id ? <CircleMembersPanel circleId={id} setSync={setSync} /> : null
        )}
      </main>
      <aside aria-label="Details">
        <AboutRail c={circle} />
        <section className="panel">
          <h3>
            👥 Organizers <span className="countpill">{orgCount}</span>
            <Link to={'/reader-circles/circle/' + circle.id + '/organizers'}>View all →</Link>
          </h3>
          {orgPrev.slice(0, 2).map((x, i) => (
            <div className="statline" key={i}>
              <span
                className="face"
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: '50%',
                  background: '#EAD9C2',
                  display: 'grid',
                  placeItems: 'center',
                  fontWeight: 700,
                }}
              >
                {initialsOf(x.name)}
              </span>{' '}
              {x.name} <span className="rolepill">Organizer</span>
            </div>
          ))}
        </section>
      </aside>
    </div>
  );
}

function CircleEventsPanel({ circleId, setSync }: { circleId: string; setSync: (s: string) => void }) {
  const id = circleId;
  const [sp, setSp] = useSearchParams();
  const scope = sp.get('scope') === 'past' ? 'past' : 'upcoming';
  const nav = useNavigate();
  const [circle, setCircle] = useState<Circle | null>(null);
  const [list, setList] = useState<CircleEvent[]>([]);
  const [allUp, setAllUp] = useState<CircleEvent[]>([]);
  const [past, setPast] = useState<CircleEvent[]>([]);
  const [qText, setQText] = useState('');
  const [q, setQ] = useState('');
  const [venue, setVenue] = useState('');
  const [month, setMonth] = useState('');
  const [mine, setMine] = useState<Membership[]>([]);
  const [orgIds, setOrgIds] = useState<Record<string, number>>({});
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');

  useEffect(() => {
    const t = window.setTimeout(() => setQ(qText), 300);
    return () => window.clearTimeout(t);
  }, [qText]);

  useEffect(() => {
    let on = true;
    if (!id) return;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const c = await api<Circle>('/api/circles/' + id);
        const m = await api<Membership[]>('/api/users/me/memberships').catch(() => [] as Membership[]);
        let org: { id?: string }[] = [];
        try {
          org = await api<{ id?: string }[]>('/api/users/me/organized-circles');
        } catch {
          org = [];
        }
        let p: string = '?scope=' + scope;
        if (venue) p += '&venue=' + encodeURIComponent(venue);
        if (month) p += '&month=' + encodeURIComponent(month);
        let scoped: CircleEvent[] = [];
        try {
          scoped = await api<CircleEvent[]>('/api/circles/' + id + '/events' + p);
        } catch {
          scoped = [];
        }
        let up: CircleEvent[] = [];
        try {
          up = await api<CircleEvent[]>('/api/circles/' + id + '/events?scope=upcoming');
        } catch {
          up = [];
        }
        let pa: CircleEvent[] = [];
        try {
          pa = await api<CircleEvent[]>('/api/circles/' + id + '/events?scope=past');
        } catch {
          pa = [];
        }
        if (!on) return;
        setCircle(c);
        setMine(m || []);
        const og: Record<string, number> = {};
        (org || []).forEach((o) => {
          if (o.id) og[String(o.id)] = 1;
        });
        setOrgIds(og);
        setList(scoped || []);
        setAllUp(up || []);
        setPast(pa || []);
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [id, scope, venue, month, tick, setSync]);

  const visible = useMemo(() => {
    if (!q) return list;
    const l = q.toLowerCase();
    return list.filter((e) => ((e.title || '') + ' ' + (e.readingTopic || '') + ' ' + (e.location || '') + ' ' + (e.venue || '')).toLowerCase().indexOf(l) >= 0);
  }, [list, q]);

  const venues = useMemo(() => {
    const m: Record<string, number> = {};
    list.concat(allUp, past).forEach((e) => {
      if (e.venue) m[e.venue] = 1;
    });
    return Object.keys(m);
  }, [list, allUp, past]);

  const months = useMemo(() => {
    const m: Record<string, number> = {};
    list.concat(allUp, past).forEach((e) => {
      if (e.eventDate) m[String(e.eventDate).slice(0, 7)] = (m[String(e.eventDate).slice(0, 7)] || 0) + 1;
    });
    return Object.keys(m).sort();
  }, [list, allUp, past]);

  async function register(eventId: string) {
    setBusyId(eventId);
    try {
      await api('/api/events/' + eventId + '/register', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  function setScope(s: string) {
    setSp(s === 'past' ? { scope: 'past' } : { scope: 'upcoming' });
  }

  async function joinCircle() {
    if (!id) return;
    try {
      await api('/api/circles/' + id + '/join-requests', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    }
  }

  const evStatus = id ? statusMap(mine)[id]?.status : undefined;
  const canRegisterEv = evStatus === 'APPROVED' || (id ? !!orgIds[id] : false);

  if (err) {
    return <div className="err">{err}</div>;
  }
  if (!circle) {
    return <div className="empty">Loading circle events…</div>;
  }

  return (
    <>
        <div className="tabs">
          <button className={scope === 'upcoming' ? 'on' : ''} onClick={() => setScope('upcoming')}>
            Upcoming Events <span className="countpill">{allUp.length}</span>
          </button>
          <button className={scope === 'past' ? 'on' : ''} onClick={() => setScope('past')}>
            Past Events <span className="countpill">{past.length}</span>
          </button>
        </div>
        <div className="toolbar">
          <div className="search" style={{ flex: 1, minWidth: 180, maxWidth: 'none' }}>
            <span className="lens">🔍</span>
            <input type="search" placeholder="Search events…" value={qText} onChange={(e) => setQText(e.target.value)} />
          </div>
          <select value={venue} onChange={(e) => setVenue(e.target.value)}>
            <option value="">All Locations</option>
            {venues.map((v) => (
              <option key={v} value={v}>
                {v}
              </option>
            ))}
          </select>{' '}
          <select value={month} onChange={(e) => setMonth(e.target.value)}>
            <option value="">All Months</option>
            {months.map((k) => (
              <option key={k} value={k}>
                {new Date(k + '-01T00:00:00').toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
              </option>
            ))}
          </select>
        </div>
        <div>
          {visible.length ? (
            visible.map((e) => (
              <div className="evrow" key={e.id}>
                <img
                  src={resolveMedia(e.coverImageUrl) || FALLBACK}
                  alt=""
                  onError={(e2) => {
                    e2.currentTarget.onerror = null;
                    e2.currentTarget.src = FALLBACK;
                  }}
                />
                <div>
                  <h4>{e.title}</h4>
                  <p>{e.description || e.readingTopic || ''}</p>
                  <div className="mrow" style={{ fontSize: 12, color: 'var(--muted)' }}>
                    👥 {e.registeredCount || 0} registered
                  </div>
                </div>
                <div>
                  <div className="evwhen">
                    <span>📅 {fmtDate(e.eventDate)}</span>
                    <span>🕐 {fmtTime(e.eventTime)}</span>
                    <span>📍 {[e.location, e.venue].filter(Boolean).join(' · ')}</span>
                  </div>
                  {e.isRegistered ? (
                    <span className="statuspill accepted">✔ Registered</span>
                  ) : !canRegisterEv ? (
                    <span className="statuspill pending">Members only</span>
                  ) : (
                    <span className="statuspill pending">🕐 Registration Open</span>
                  )}
                  <div style={{ marginTop: 8 }}>
                    {scope === 'upcoming' ? (
                      e.isRegistered ? (
                        <button
                          className="cardbtn"
                          style={{
                            padding: '7px 14px',
                            fontSize: 12.5,
                            background: '#F0E8DC',
                            color: 'var(--ink)',
                            cursor: 'pointer',
                            whiteSpace: 'nowrap',
                          }}
                          onClick={() => nav('/reader-events/event/' + e.id)}
                        >
                          View Event →
                        </button>
                      ) : !canRegisterEv ? (
                        evStatus === 'PENDING' ? (
                          <button className="cardbtn solid" disabled>
                            Requested — pending
                          </button>
                        ) : (
                          <button className="cardbtn solid" style={{ background: '#F3E8D8', color: 'var(--ink)' }} onClick={() => void joinCircle()}>
                            🔒 Join Circle to Attend
                          </button>
                        )
                      ) : (
                        <button className="cardbtn solid" disabled={busyId === e.id} onClick={() => register(e.id)}>
                          {busyId === e.id ? 'Registering…' : 'Register Event →'}
                        </button>
                      )
                    ) : null}
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="empty">No {scope} events.</div>
          )}
        </div>
    </>
  );
}

function OrganizersView({ setSync }: { setSync: (s: string) => void }) {
  const { id } = useParams<{ id: string }>();
  const [circle, setCircle] = useState<Circle | null>(null);
  const [orgs, setOrgs] = useState<OrganizersResp>({});
  const [upcoming, setUpcoming] = useState(0);
  const [memTotal, setMemTotal] = useState(0);
  const [err, setErr] = useState('');

  useEffect(() => {
    let on = true;
    if (!id) return;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const c = await api<Circle>('/api/circles/' + id);
        let ov: Overview | null = null;
        try {
          ov = await api<Overview>('/api/circles/' + id + '/overview');
        } catch {
          ov = null;
        }
        const map = await upcomingCounts();
        const o = await api<OrganizersResp>('/api/circles/' + id + '/organizers');
        let mt = 0;
        try {
          const s = await api<MemberStats>('/api/circles/' + id + '/member-stats');
          mt = Number(s.totalMembers || 0);
        } catch {
          mt = 0;
        }
        if (!on) return;
        setCircle(c);
        setOrgs(o || {});
        setUpcoming(map[id] || Number((ov && ov.counts && ov.counts.upcomingEvents) || 0));
        setMemTotal(mt || Number(c.memberCount || 0));
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [id, setSync]);

  function card(x: Organizer, past: boolean, key: number) {
    return (
      <div className="orgrow" key={key}>
        <img
          src={x.avatarUrl || FALLBACK}
          alt=""
          onError={(e) => {
            e.currentTarget.onerror = null;
            e.currentTarget.src = FALLBACK;
          }}
        />
        <div>
          <h4>
            {x.name} {past ? '' : '👑'}
          </h4>
          <span className={'rolepill' + (past ? ' member' : '')}>{past ? 'Former Organizer' : 'Organizer'}</span>
          <div className="loc">📍 {x.city ? x.city + ', TN' : ''}</div>
          <div className="loc">📅 Joined {fmtDate(x.joinedAt || x.addedAt)}</div>
        </div>
        <p className="bio">{x.bio || ''}</p>
        <div className="tagrow">{((x.interests || []) as string[]).map((t, i) => (
          <span key={i}>{t}</span>
        ))}</div>
      </div>
    );
  }

  if (err) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="err">{err}</div>
        </main>
        <aside />
      </div>
    );
  }
  if (!circle) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="empty">Loading organizers…</div>
        </main>
        <aside />
      </div>
    );
  }

  const cur = orgs.current || [];
  const past = orgs.past || [];
  const curCount = Number(orgs.currentCount ?? cur.length);
  const pastCount = Number(orgs.pastCount ?? past.length);

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <Link className="backlink" to={'/reader-circles/circle/' + circle.id}>
          ← Back to {circle.name}
        </Link>
        <h1 className="page">
          Circle Organizers <span className="countpill">{curCount}</span>
        </h1>
        <p className="sub">People who manage and organize this circle.</p>
        <div className="secttl">
          <h2>👥 Current Organizers</h2>
          <span className="countpill">{curCount}</span>
        </div>
        {cur.length ? cur.map((x, i) => card(x, false, i)) : <div className="empty">No organizers.</div>}
        <div className="secttl">
          <h2>🕐 Past Organizers</h2>
          <span className="countpill">{pastCount}</span>
        </div>
        {past.length ? past.map((x, i) => card(x, true, i)) : <div className="empty">No past organizers.</div>}
      </main>
      <aside aria-label="Details">
        <AboutRail c={circle} />
        <section className="panel">
          <h3>
            👥 Members <span className="countpill">{memTotal}</span>
            <Link to={'/reader-circles/circle/' + circle.id + '/members'}>View all →</Link>
          </h3>
          <div className="avatars">
            <span>+{Math.max(0, memTotal)}</span>
          </div>
        </section>
        <section className="panel">
          <h3>
            📅 Upcoming Events <span className="countpill">{upcoming}</span>
            <Link to={'/reader-circles/circle/' + circle.id + '/events'}>View all →</Link>
          </h3>
        </section>
        <section className="panel">
          <h3>📊 Circle Stats</h3>
          <div className="minis">
            <div className="mini">
              <b>{circle.memberCount}</b>
              <span>Members</span>
            </div>
            <div className="mini">
              <b>{upcoming}</b>
              <span>Events</span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  );
}

function CircleMembersPanel({ circleId, setSync }: { circleId: string; setSync: (s: string) => void }) {
  const id = circleId;
  const [ready, setReady] = useState(false);
  const [all, setAll] = useState<CircleMember[]>([]);
  const [orgIds, setOrgIds] = useState<Record<string, number>>({});
  const [qText, setQText] = useState('');
  const [q, setQ] = useState('');
  const [role, setRole] = useState('all');
  const [err, setErr] = useState('');

  useEffect(() => {
    const t = window.setTimeout(() => setQ(qText), 300);
    return () => window.clearTimeout(t);
  }, [qText]);

  useEffect(() => {
    let on = true;
    if (!id) return;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        let p = '?';
        if (role && role !== 'all') p += 'role=' + role + '&';
        const list = await api<CircleMember[]>('/api/circles/' + id + '/members' + p);
        let orgs: Organizer[] = [];
        try {
          const o = await api<OrganizersResp>('/api/circles/' + id + '/organizers');
          orgs = (o && o.current) || [];
        } catch {
          orgs = [];
        }
        if (!on) return;
        setReady(true);
        const m: Record<string, number> = {};
        orgs.forEach((o) => {
          if (o.userId) m[o.userId] = 1;
        });
        setOrgIds(m);
        const l = q
          ? (list || []).filter((x) => ((x.readerName || '') + ' ' + (x.bio || '')).toLowerCase().indexOf(q.toLowerCase()) >= 0)
          : list || [];
        setAll(l);
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [id, role, q, setSync]);

  if (err) {
    return <div className="err">{err}</div>;
  }
  if (!ready) {
    return <div className="empty">Loading members…</div>;
  }

  return (
    <>
        <div className="toolbar">
          <div className="search" style={{ flex: 1, minWidth: 180, maxWidth: 'none' }}>
            <span className="lens">🔍</span>
            <input type="search" placeholder="Search members…" value={qText} onChange={(e) => setQText(e.target.value)} />
          </div>
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="all">All Roles</option>
            <option value="ORGANIZER">Organizer</option>
            <option value="READER">Reader</option>
          </select>
        </div>
        <div>
          {all.length ? (
            all.map((x, i) => {
              const isOrg = !!x.readerId && !!orgIds[x.readerId];
              return (
                <div className="memrow" key={i}>
                  <img
                    src={x.avatarUrl || FALLBACK}
                    alt=""
                    onError={(e) => {
                      e.currentTarget.onerror = null;
                      e.currentTarget.src = FALLBACK;
                    }}
                  />
                  <div>
                    <h4>
                      {x.readerName}
                      {isOrg ? ' 👑' : ''}
                    </h4>
                    <span className={'rolepill' + (isOrg ? '' : ' member')}>{isOrg ? 'Organizer' : 'Member'}</span>
                    <div className="loc">📍 {x.city ? x.city + ', TN' : ''}</div>
                    <div className="loc">📅 Joined {fmtDate(x.requestedAt)}</div>
                  </div>
                  <div>
                    <p className="bio" style={{ fontSize: 13, margin: 0 }}>
                      {x.bio || ''}
                    </p>
                    <div className="tagrow" style={{ marginTop: 6 }}>
                      {((x.interests || []) as string[]).map((t, j) => (
                        <span key={j}>{t}</span>
                      ))}
                    </div>
                  </div>
                  <div />
                </div>
              );
            })
          ) : (
            <div className="empty">No members match.</div>
          )}
        </div>
    </>
  );
}

export default function ReaderCircles() {
  const [sync, setSync] = useState('Connecting…');

  return (
    <ReaderLayout
      active="circles"
      syncNote={sync}
    >
      <Routes>
        <Route index element={<MyView setSync={setSync} />} />
        <Route path="my" element={<MyView setSync={setSync} />} />
        <Route path="explore" element={<ExploreView externalQ="" setSync={setSync} />} />
        <Route path="requests" element={<RequestsView setSync={setSync} />} />
        <Route path="circle/:id" element={<DetailView setSync={setSync} />} />
        <Route path="manage/:id" element={<ManageArea />} />
        <Route path="circle/:id/events" element={<DetailView setSync={setSync} initTab="events" />} />
        <Route path="circle/:id/organizers" element={<OrganizersView setSync={setSync} />} />
        <Route path="circle/:id/members" element={<DetailView setSync={setSync} initTab="members" />} />
      </Routes>
    </ReaderLayout>
  );
}
