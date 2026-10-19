import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, fmtDate, fmtTime, initialsOf, resolveMedia } from '../lib/api';
import ReaderLayout from '../components/ReaderLayout';
import { EventForm, EventModal, type MEvent } from './ManageEvents';
import EventGallery from './EventGallery';
import './ReaderEvents.css';
import './CircleManage.css';

import { COVER_PLACEHOLDER as FALLBACK } from '../lib/placeholders';

type Circle = {
  id: string;
  name: string;
  city?: string;
  memberCount?: number;
  coverImageUrl?: string;
};

type Membership = {
  id?: string;
  circleId: string;
  status: string;
};

type EventItem = {
  id: string;
  circleId?: string;
  circleName?: string;
  title?: string;
  coverImageUrl?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  mode?: string;
  type?: string;
  readingTopic?: string;
  status?: string;
  registeredCount?: number;
  capacity?: number;
  isRegistered?: boolean;
  description?: string;
  createdAt?: string;
};

type Registration = {
  eventId: string;
  status?: string;
  attended?: boolean;
};

type Attendee = {
  name?: string;
  avatarUrl?: string;
};

type ChatMsg = {
  author?: string;
  avatarUrl?: string;
  content?: string;
  createdAt?: string;
};

type MsgsResp = {
  messages?: ChatMsg[];
  count?: number;
  chatOpen?: boolean;
  chatClosesAt?: string;
};

type EventStats = {
  upcoming?: number;
  registered?: number;
  completed?: number;
  totalAttendees?: number;
  attended?: number;
  missed?: number;
};

type Tab = 'upcoming' | 'registered' | 'past';

function typeLabel(t?: string): string {
  return String(t || '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Event place for display: district (location) plus the specific venue. */
function placeOf(e: { location?: string; venue?: string }): string {
  return [e.location, e.venue].filter(Boolean).join(' · ');
}

function evTagList(e: EventItem): string[] {
  const t: string[] = [];
  if (e.type) t.push(typeLabel(e.type));
  const topic = String(e.readingTopic || '')
    .split(/[,·]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 2);
  topic.forEach((x) => {
    if (t.indexOf(x) < 0) t.push(x);
  });
  return t.slice(0, 3);
}

function timeLeft(iso?: string): string {
  if (!iso) return '';
  const diff = new Date(iso.length <= 10 ? iso + 'T00:00:00' : iso).getTime() - Date.now();
  if (diff <= 0) return 'Happening now';
  const d = Math.floor(diff / 86400000);
  if (d === 0) return 'Today';
  if (d === 1) return 'In a day';
  return 'In ' + d + ' days';
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function Faces({ preview, total }: { preview: Attendee[]; total: number }) {
  return (
    <div className="avatars">
      {preview.slice(0, 4).map((a, i) =>
        a.avatarUrl ? (
          <span key={i}>
            <img
              src={a.avatarUrl}
              alt=""
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = FALLBACK;
              }}
            />
          </span>
        ) : (
          <span key={i}>{initialsOf(a.name)}</span>
        ),
      )}
      {Math.max(0, total - 4) ? <span>+{Math.max(0, total - 4)}</span> : null}
    </div>
  );
}

function EventTabs({ tab, counts }: { tab: Tab; counts: { upcoming: number; registered: number; past: number } }) {
  const nav = useNavigate();
  function btn(k: Tab, label: string, n: number) {
    const to = k === 'upcoming' ? '/reader-events' : '/reader-events/' + k;
    return (
      <button className={tab === k ? 'on' : ''} onClick={() => nav(to)}>
        {label} <span className="cbadge">{n}</span>
      </button>
    );
  }
  return (
    <div className="tabs" role="tablist">
      {btn('upcoming', 'Upcoming', counts.upcoming)}
      {btn('registered', 'Registered', counts.registered)}
      {btn('past', 'Past', counts.past)}
    </div>
  );
}

export function MonthCal({ dates }: { dates: Record<string, number> }) {
  const [key, setKey] = useState(() => new Date().toISOString().slice(0, 7));
  const y = +key.slice(0, 4);
  const mo = +key.slice(5, 7) - 1;
  const base = new Date(y, mo, 1);
  const label = base.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
  const startDay = base.getDay();
  const days = new Date(y, mo + 1, 0).getDate();
  const prev = new Date(y, mo, 0).getDate();
  const cells: React.ReactNode[] = [];
  for (let i = startDay - 1; i >= 0; i--) {
    cells.push(
      <td className="dim" key={'p' + i}>
        {prev - i}
      </td>,
    );
  }
  for (let d = 1; d <= days; d++) {
    const k = y + '-' + String(mo + 1).padStart(2, '0') + '-' + String(d).padStart(2, '0');
    cells.push(
      dates[k] ? (
        <td className="ev" key={k}>
          {d}
        </td>
      ) : (
        <td key={k}>{d}</td>
      ),
    );
  }
  const rows: React.ReactNode[] = [];
  for (let i = 0; i < cells.length; i += 7) {
    rows.push(<tr key={'r' + i}>{cells.slice(i, i + 7)}</tr>);
  }
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <button
          className="ghostbtn"
          onClick={() => setKey(new Date(y, mo - 1, 1).toISOString().slice(0, 7))}
        >
          ‹
        </button>
        <b>{label}</b>
        <button
          className="ghostbtn"
          onClick={() => setKey(new Date(y, mo + 1, 1).toISOString().slice(0, 7))}
        >
          ›
        </button>
      </div>
      <table className="cal">
        <thead>
          <tr>
            <th>Su</th>
            <th>Mo</th>
            <th>Tu</th>
            <th>We</th>
            <th>Th</th>
            <th>Fr</th>
            <th>Sa</th>
          </tr>
        </thead>
        <tbody>{rows}</tbody>
      </table>
    </div>
  );
}

type Filters = { q: string; circle: string; type: string; loc: string; chennai: boolean };

function EventList({ tab, externalQ, setSync }: { tab: Tab; externalQ: string; setSync: (s: string) => void }) {
  const nav = useNavigate();
  const [f, setF] = useState<Filters>({ q: '', circle: '', type: '', loc: '', chennai: false });
  const [qText, setQText] = useState('');
  const [circles, setCircles] = useState<Circle[]>([]);
  const [okMap, setOkMap] = useState<Record<string, number>>({});
  const [stMap, setStMap] = useState<Record<string, string>>({});
  const [midMap, setMidMap] = useState<Record<string, string>>({});
  const [orgMap, setOrgMap] = useState<Record<string, number>>({});
  const [regMap, setRegMap] = useState<Record<string, Registration>>({});
  const [list, setList] = useState<EventItem[]>([]);
  const [previews, setPreviews] = useState<Record<string, Attendee[]>>({});
  const [counts, setCounts] = useState({ upcoming: 0, registered: 0, past: 0 });
  const [stats, setStats] = useState<EventStats | null>(null);
  const [allForCal, setAllForCal] = useState<EventItem[]>([]);
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    setF((p) => ({ ...p, q: externalQ || '' }));
    setQText(externalQ || '');
  }, [externalQ]);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setF((p) => (p.q === qText ? p : { ...p, q: qText }));
    }, 350);
    return () => window.clearTimeout(t);
  }, [qText]);

  useEffect(() => {
    let on = true;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        let st: EventStats | null = null;
        try {
          st = await api<EventStats>('/api/users/me/event-stats');
        } catch {
          st = null;
        }
        try {
          const me = await api<{ role?: string }>('/api/users/me');
          if (on && me?.role === 'ADMIN') setIsAdmin(true);
        } catch {
          /* ignore */
        }
        const cs = await api<Circle[]>('/api/circles?sort=name_asc');
        const mine = await api<Membership[]>('/api/users/me/memberships');
        let regs: Registration[] = [];
        try {
          regs = await api<Registration[]>('/api/users/me/registrations');
        } catch {
          regs = [];
        }
        const ok: Record<string, number> = {};
        const stMap: Record<string, string> = {};
        const midMap: Record<string, string> = {};
        (mine || []).forEach((m) => {
          if (m.circleId) {
            stMap[m.circleId] = m.status;
            if (m.id) midMap[m.circleId] = m.id;
          }
          if (m.status === 'APPROVED') ok[m.circleId] = 1;
        });
        // Organisers have no membership row (backend forbids joining own
        // circle) — but they ARE allowed to register for its events, so
        // treat circles they organise as "member" here.
        let organised: Circle[] = [];
        try {
          organised = await api<Circle[]>('/api/users/me/organized-circles');
        } catch {
          organised = [];
        }
        const org: Record<string, number> = {};
        (organised || []).forEach((c) => {
          if (c.id) org[c.id] = 1;
        });
        const rm: Record<string, Registration> = {};
        regs.forEach((r) => {
          rm[r.eventId] = r;
        });
        let p = '?scope=' + (tab === 'past' ? 'past' : tab === 'registered' ? 'upcoming' : 'all');
        if (f.q) p += '&search=' + encodeURIComponent(f.q);
        if (f.circle) p += '&circleId=' + encodeURIComponent(f.circle);
        if (f.type) p += '&types=' + encodeURIComponent(f.type);
        if (f.loc) p += '&mode=' + encodeURIComponent(f.loc);
        let data = await api<EventItem[]>('/api/events' + p);
        if (f.chennai) data = data.filter((e) => String(e.venue || '').toLowerCase().indexOf('chennai') >= 0);
        if (tab === 'registered') data = data.filter((e) => rm[e.id]);
        if (tab === 'past') data = data.filter((e) => e.status === 'COMPLETED');
        if (tab === 'upcoming') data = data.filter((e) => e.status === 'PUBLISHED');
        const pvMap: Record<string, Attendee[]> = {};
        await Promise.all(
          data.map((e) =>
            api<Attendee[]>('/api/events/' + e.id + '/attendee-preview')
              .then((pv) => {
                pvMap[e.id] = pv || [];
              })
              .catch(() => {
                pvMap[e.id] = [];
              }),
          ),
        );
        let all: EventItem[] = [];
        try {
          all = await api<EventItem[]>('/api/events?scope=all');
        } catch {
          all = [];
        }
        if (!on) return;
        setCircles(cs || []);
        setOkMap(ok);
        setStMap(stMap);
        setMidMap(midMap);
        setOrgMap(org);
        setRegMap(rm);
        setList(data || []);
        setPreviews(pvMap);
        setAllForCal(all || []);
        setStats(st);
        // Badges mirror the actual lists in each tab: upcoming events for the
        // circles you see, your still-upcoming registrations, and completed
        // (past) events. A crossed event leaves Upcoming/Registered for Past.
        setCounts({
          upcoming: all.filter((e) => e.status === 'PUBLISHED').length,
          registered: all.filter((e) => rm[e.id] && e.status === 'PUBLISHED').length,
          past: all.filter((e) => e.status === 'COMPLETED').length,
        });
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
  }, [tab, f, tick, setSync]);

  const calDates = useMemo(() => {
    const m: Record<string, number> = {};
    allForCal.forEach((e) => {
      if (e.eventDate) m[String(e.eventDate).slice(0, 10)] = 1;
    });
    return m;
  }, [allForCal]);


  async function register(id: string) {
    setBusyId(id);
    try {
      await api('/api/events/' + id + '/register', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  async function joinCircle(e: EventItem) {
    if (!e.circleId) return;
    setBusyId(e.id);
    try {
      await api('/api/circles/' + e.circleId + '/join-requests', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (err) {
      alert(errMsg(err));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  async function cancelJoin(e: EventItem) {
    const mid = midMap[e.circleId || ''];
    if (!mid) return;
    setBusyId(e.id);
    try {
      await api('/api/join-requests/' + mid, { method: 'DELETE' });
      setTick((t) => t + 1);
    } catch (err) {
      alert(errMsg(err));
      setTick((t) => t + 1);
    } finally {
      setBusyId('');
    }
  }

  function openEvent(id: string, view?: 'chat' | 'gallery') {
    const returnUrl = tab === 'upcoming' ? '/reader-events' : '/reader-events/' + tab;
    nav('/reader-events/event/' + id, { state: { from: returnUrl, view } });
  }

  function chat(id: string) {
    openEvent(id, 'chat');
  }

  function actionBtn(e: EventItem, okMember: boolean) {
    if (isAdmin) {
      return (
        <button className="oabtn primary" onClick={() => chat(e.id)}>
          👁&nbsp;&nbsp;View Event
        </button>
      );
    }
    const full = e.capacity != null && Number(e.registeredCount || 0) >= Number(e.capacity);
    if (e.isRegistered) {
      return (
        <>
          <button className="oabtn" disabled>
            ✔ Registered
          </button>
          <button className="oabtn primary" onClick={() => chat(e.id)}>
            👁&nbsp;&nbsp;View Event
          </button>
        </>
      );
    }
    if (full) return <button className="oabtn" disabled>🚫 Event Full</button>;
    if (!okMember) {
      const pending = stMap[e.circleId || ''] === 'PENDING';
      return (
        <>
          <button className="oabtn" disabled>
            🔒 Join Circle to Attend
          </button>
          {pending ? (
            <>
              <button className="oabtn" disabled>
                🕐 Requested — pending
              </button>
              <button className="oabtn" disabled={busyId === e.id} onClick={() => void cancelJoin(e)}>
                {busyId === e.id ? 'Cancelling…' : '✕ Cancel Request'}
              </button>
            </>
          ) : (
            <button className="oabtn primary" disabled={busyId === e.id} onClick={() => void joinCircle(e)}>
              {busyId === e.id ? 'Requesting…' : '➕ Request to Join the Circle'}
            </button>
          )}
        </>
      );
    }
    return (
      <button className="oabtn primary" disabled={busyId === e.id} onClick={() => register(e.id)}>
        {busyId === e.id ? 'Registering…' : '🧍‍♂️  Register for Event'}
      </button>
    );
  }

  function upCard(e: EventItem) {
    const isOrg = !!orgMap[e.circleId || ''];
    const isMem = !!okMap[e.circleId || ''];
    return (
      <div className="ocard" key={e.id}>
        <div className="odate">
          <span className="odate-m">{monShort(e.eventDate)}</span>
          <b>{dayNum(e.eventDate)}</b>
          <span className="odate-d">{dowShort(e.eventDate)}</span>
        </div>
        <img
          className="ocover"
          src={resolveMedia(e.coverImageUrl) || FALLBACK}
          alt={'Cover of ' + (e.title || 'event')}
          onError={(e2) => {
            e2.currentTarget.onerror = null;
            e2.currentTarget.src = FALLBACK;
          }}
        />
        <div className="omain">
          <div className="orow">
            <span className="opill">{e.circleName || ''}</span>
            {isOrg ? (
              <span className="rolebadge org">👑 Organizer</span>
            ) : isMem ? (
              <span className="rolebadge mem">👤 Member</span>
            ) : null}
          </div>
          <h3>{e.title}</h3>
          {e.description ? <p className="odesc">{e.description}</p> : null}
          <div className="ometa">
            <span className="m">📅 {fmtDate(e.eventDate)}</span>
            <span className="m">🕐 {fmtTime(e.eventTime)}</span>
            <span className="m">📍 {placeOf(e) || '—'}</span>
          </div>
          <div className="ofoot">
            <div className="oreg">
              <Faces preview={previews[e.id] || []} total={Number(e.registeredCount || 0)} />
              <span>
                <b>{e.registeredCount || 0}</b>
                {e.capacity ? ` / ${e.capacity}` : ''} registered
              </span>
            </div>
            {evTagList(e).length ? (
              <div className="otags">
                {evTagList(e).map((t, i) => (
                  <span key={i}>{t}</span>
                ))}
              </div>
            ) : null}
          </div>
        </div>
        <div className="oactions">{actionBtn(e, isMem || isOrg)}</div>
      </div>
    );
  }

  function regCard(e: EventItem) {
    const r = regMap[e.id] || {};
    void r;
    return (
      <div className="ecard" style={{ gridColumn: '1/-1' }} key={e.id}>
        <div className="bigrow">
          <img
            src={e.coverImageUrl || FALLBACK}
            alt={'Cover of ' + (e.title || 'event')}
            onError={(e2) => {
              e2.currentTarget.onerror = null;
              e2.currentTarget.src = FALLBACK;
            }}
          />
          <div className="bigrow-main">
            <h3>{e.title}</h3>
            <div className="circ">👑 {e.circleName || ''}</div>
            <div className="when">
              📅 {fmtDate(e.eventDate)} · {fmtTime(e.eventTime)}
              <br />📍 {placeOf(e)}
            </div>
            <div className="tagrow">
              {evTagList(e).map((t, i) => (
                <span key={i}>{t}</span>
              ))}
            </div>
            <div className="attrow">
              <Faces preview={previews[e.id] || []} total={Number(e.registeredCount || 0)} />
              <span>{e.registeredCount || 0} registered</span>
            </div>
          </div>
          <div className="sidecol">
            <div className="sidecol-head">
              <span className="statuspill accepted">✔ Registered</span>
              <span className="time-badge">⏱ {timeLeft(e.eventDate)}</span>
            </div>
            <div className="sidecol-info">
              <div className="sidecol-item">
                <span className="sc-icon">👥</span>
                <div>
                  <div className="sc-val">{e.registeredCount || 0} registered</div>
                  <div className="sc-sub">{e.capacity ? `${e.capacity} slots total` : 'Open capacity'}</div>
                </div>
              </div>
              <div className="sidecol-item">
                <span className="sc-icon">📍</span>
                <div>
                  <div className="sc-val">{e.mode === 'ONLINE' ? 'Online Event' : 'Offline Event'}</div>
                  <div className="sc-sub">{placeOf(e) || 'No venue specified'}</div>
                </div>
              </div>
            </div>
            <div className="slotbtn">
              <button className="cardbtn done" disabled>
                ✔ Registered
              </button>
              <button className="chatbtn" onClick={() => chat(e.id)}>
                👁 View Event
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  function pastCard(e: EventItem) {
    const isOrg = !!orgMap[e.circleId || ''] || isAdmin;
    const rr = regMap[e.id];
    // Anyone who registered or admin is assumed to have attended
    const isReg = !!(rr && rr.status === 'REGISTERED') || !!e.isRegistered || isAdmin;

    const pill = isAdmin ? (
      <span className="statuspill accepted">👑 Admin</span>
    ) : isOrg ? (
      <span className="statuspill accepted">👑 Organiser</span>
    ) : isReg ? (
      <span className="statuspill accepted">✔ Attended</span>
    ) : (
      <span className="statuspill unregistered">🔒 Event Not Registered</span>
    );

    let slotButton: React.ReactNode;
    if (isAdmin || isOrg || isReg) {
      slotButton = (
        <button className="cardbtn" onClick={() => chat(e.id)}>
          👁 View Event
        </button>
      );
    } else {
      slotButton = (
        <button className="cardbtn locked" disabled title="You were not registered for this event">
          🔒 Event Not Registered
        </button>
      );
    }

    return (
      <div className="ecard" style={{ gridColumn: '1/-1' }} key={e.id}>
        <div className="bigrow">
          <img
            src={e.coverImageUrl || FALLBACK}
            alt={'Cover of ' + (e.title || 'event')}
            onError={(e2) => {
              e2.currentTarget.onerror = null;
              e2.currentTarget.src = FALLBACK;
            }}
          />
          <div className="bigrow-main">
            <h3>{e.title}</h3>
            <div className="circ">👑 {e.circleName || ''}</div>
            <div className="when">
              📅 {fmtDate(e.eventDate)} · {fmtTime(e.eventTime)}
              <br />📍 {placeOf(e)}
            </div>
            <div className="tagrow">
              {evTagList(e).map((t, i) => (
                <span key={i}>{t}</span>
              ))}
            </div>
            <div className="attrow">
              <Faces preview={previews[e.id] || []} total={Number(e.registeredCount || 0)} />
              <span>{e.registeredCount || 0} attended</span>
            </div>
          </div>
          <div className="sidecol">
            <div className="sidecol-head">
              {pill}
              <span className="time-badge">Completed</span>
            </div>
            <div className="sidecol-info">
              <div className="sidecol-item">
                <span className="sc-icon">👥</span>
                <div>
                  <div className="sc-val">{e.registeredCount || 0} / {e.capacity || '—'}</div>
                  <div className="sc-sub">members attended</div>
                </div>
              </div>
            </div>
            <div className="slotbtn">
              {slotButton}
            </div>
          </div>
        </div>
      </div>
    );
  }

  const subs: Record<Tab, string> = {
    upcoming: 'Discover and join reading events from the circles you\u2019re part of.',
    registered: 'Your registered events from the circles you\u2019re part of.',
    past: 'Past events from the circles you\u2019re part of.',
  };

  const onlineOn = f.loc === 'ONLINE';

  let statHtml: React.ReactNode;
  if (tab === 'registered' && stats) {
    statHtml = (
      <section className="panel">
        <h3>📅 Event Stats</h3>
        <div className="minis">
          <div className="mini"><b>{stats.registered}</b><span>Registered</span></div>
          <div className="mini"><b>{stats.upcoming}</b><span>Upcoming</span></div>
          <div className="mini"><b>{stats.completed}</b><span>Completed</span></div>
          <div className="mini"><b>{stats.totalAttendees}</b><span>Total Attendees</span></div>
        </div>
      </section>
    );
  } else if (tab === 'past' && stats) {
    statHtml = (
      <section className="panel">
        <h3>📅 Event Stats</h3>
        <div className="minis">
          <div className="mini"><b>{stats.registered}</b><span>Total Events</span></div>
          <div className="mini"><b>{stats.attended}</b><span>Attended</span></div>
          <div className="mini"><b>{stats.missed}</b><span>Missed</span></div>
          <div className="mini"><b>{stats.totalAttendees}</b><span>Total Attendees</span></div>
        </div>
      </section>
    );
  } else {
    const onlineCount = allForCal.filter(
      (e) => String(e.mode || '').toUpperCase() === 'ONLINE' || String(e.venue || '').toLowerCase().indexOf('online') >= 0,
    ).length;
    void onlineCount;
    statHtml = (
      <section className="panel">
        <h3>🔻 Filters</h3>
        <div className="frow">
          <b>Location</b>
          <label className="check">
            <input
              type="checkbox"
              checked={onlineOn}
              onChange={() => setF((p) => ({ ...p, loc: p.loc === 'ONLINE' ? '' : 'ONLINE' }))}
            />{' '}
            Online
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={f.chennai}
              onChange={() => setF((p) => ({ ...p, chennai: !p.chennai }))}
            />{' '}
            Chennai
          </label>
        </div>
        <div className="frow">
          <b>Circle</b>
          <select value={f.circle} onChange={(e) => setF((p) => ({ ...p, circle: e.target.value }))}>
            <option value="">All Circles</option>
            {circles.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
        </div>
      </section>
    );
  }

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <h1 className="page">Events</h1>
        <p className="sub">{subs[tab]}</p>
        <EventTabs tab={tab} counts={counts} />
        <div className="toolbar">
          <div className="search">
            <span className="lens">🔍</span>
            <input type="search" placeholder="Search events…" value={qText} onChange={(e) => setQText(e.target.value)} />
          </div>
          <select value={f.circle} onChange={(e) => setF((p) => ({ ...p, circle: e.target.value }))}>
            <option value="">All Circles</option>
            {circles.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
          <select value={f.loc} onChange={(e) => setF((p) => ({ ...p, loc: e.target.value }))}>
            <option value="">All Locations</option>
            <option value="ONLINE">Online</option>
            <option value="OFFLINE">Offline</option>
          </select>
        </div>
        {err ? (
          <div className="err">{err}</div>
        ) : (
          <div className={tab === 'upcoming' ? 'olist' : 'evgrid'}>
            {!list.length ? (
              <div className="empty">No {tab} events.</div>
            ) : tab === 'registered' ? (
              list.map((e) => regCard(e))
            ) : tab === 'past' ? (
              list.map((e) => pastCard(e))
            ) : (
              list.map((e) => upCard(e))
            )}
          </div>
        )}
      </main>
      <aside aria-label="Details">
        <section className="panel">
          <h3>📅 Event Calendar</h3>
          <MonthCal dates={calDates} />
        </section>
        {statHtml}
      </aside>
    </div>
  );
}

export function EventDetail({
  setSync,
  eventId,
  asOrganizer,
  onBack,
  initialView,
}: {
  setSync: (s: string) => void;
  eventId?: string;
  asOrganizer?: boolean;
  onBack?: () => void;
  initialView?: 'overview' | 'event' | 'gallery';
}) {
  const params = useParams<{ id: string }>();
  const id = eventId ?? params.id;
  const nav = useNavigate();
  const location = useLocation();
  const navState = location.state as { from?: string; view?: 'chat' | 'gallery' } | null;
  const backTo = navState?.from;
  const [ev, setEv] = useState<EventItem | null>(null);
  const [circle, setCircle] = useState<{ coverImageUrl?: string; memberCount?: number }>({});
  const [pv, setPv] = useState<Attendee[]>([]);
  const [msgs, setMsgs] = useState<MsgsResp>({ messages: [], count: 0 });
  const [isReg, setIsReg] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [err, setErr] = useState('');
  const [msgIn, setMsgIn] = useState('');
  const [meName, setMeName] = useState('');
  const [tick, setTick] = useState(0);
  const [isOrg, setIsOrg] = useState(false);
  const [editData, setEditData] = useState<MEvent | null>(null);
  const [mgView, setMgView] = useState<'overview' | 'event' | 'edit' | 'attendees' | 'gallery'>(
    initialView === 'event' || initialView === 'gallery' ? initialView : navState?.view === 'gallery' ? 'gallery' : 'overview'
  );
  const [rView, setRView] = useState<'chat' | 'gallery'>(
    initialView === 'gallery' || navState?.view === 'gallery' ? 'gallery' : 'chat'
  );
  const [cancelling, setCancelling] = useState(false);
  const chatListRef = useRef<HTMLDivElement>(null);

  // Honour a "view" hint passed when opening the event (e.g. gallery buttons or initialView prop).
  useEffect(() => {
    const v = (location.state as { view?: string } | null)?.view || initialView;
    if (v === 'gallery') {
      setRView('gallery');
      setMgView('gallery');
    } else if (v === 'chat' || v === 'event') {
      setRView('chat');
      setMgView('event');
    } else if (v === 'overview') {
      setMgView('overview');
    }
  }, [location.state, initialView]);

  useEffect(() => {
    if (chatListRef.current) {
      chatListRef.current.scrollTop = chatListRef.current.scrollHeight;
    }
  }, [msgs.messages]);

  useEffect(() => {
    let on = true;
    if (!id) return;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const e = await api<EventItem>('/api/events/' + id);
        let c: { coverImageUrl?: string; memberCount?: number } = {};
        try {
          c = await api<Circle>('/api/circles/' + e.circleId);
        } catch {
          c = {};
        }
        let preview: Attendee[] = [];
        try {
          preview = await api<Attendee[]>('/api/events/' + id + '/attendee-preview');
        } catch {
          preview = [];
        }
        let m: MsgsResp = { messages: [], count: 0 };
        try {
          m = await api<MsgsResp>('/api/events/' + id + '/messages');
        } catch {
          m = { messages: [], count: 0 };
        }
        if (!on) return;
        setEv(e);
        setCircle(c);
        setPv(preview || []);
        setMsgs(m || { messages: [], count: 0 });
        let adminUser = false;
        try {
          const me = await api<{ name?: string; role?: string }>('/api/users/me');
          if (on && me?.name) setMeName(me.name);
          if (me?.role === 'ADMIN') {
            adminUser = true;
            if (on) setIsAdmin(true);
          }
        } catch {
          /* ignore */
        }
        setIsReg(!!e.isRegistered || adminUser);
        try {
          const orgs = await api<Circle[]>('/api/users/me/organized-circles');
          if (on) setIsOrg(!!asOrganizer || adminUser || (orgs || []).some((c) => c.id === e.circleId));
        } catch {
          if (on) setIsOrg(!!asOrganizer || adminUser);
        }
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [id, tick, setSync, asOrganizer]);

  const isChatClosed = useMemo(() => {
    if (typeof msgs.chatOpen === 'boolean') {
      return !msgs.chatOpen;
    }
    if (!ev?.eventDate) return false;
    const timeStr = ev.eventTime ? (ev.eventTime.length === 5 ? ev.eventTime + ':00' : ev.eventTime) : '00:00:00';
    const evDateTime = new Date(`${ev.eventDate}T${timeStr}`);
    if (isNaN(evDateTime.getTime())) return false;
    const closesAt = evDateTime.getTime() + 48 * 60 * 60 * 1000;
    return Date.now() > closesAt;
  }, [msgs.chatOpen, ev?.eventDate, ev?.eventTime]);

  async function sendMsg() {
    if (!id || !msgIn.trim() || isChatClosed) return;
    try {
      await api('/api/events/' + id + '/messages', { method: 'POST', body: JSON.stringify({ content: msgIn.trim() }) });
      setMsgIn('');
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
    }
  }

  async function openEdit() {
    if (!id) return;
    if (mgView === 'edit') {
      setMgView('overview');
      return;
    }
    try {
      const d = await api<MEvent>('/api/events/' + id);
      setEditData(d);
      setMgView('edit');
    } catch (e) {
      alert(errMsg(e));
    }
  }

  async function cancelEvent() {
    if (!id) return;
    if (!window.confirm('Cancel this event? Registered members will see it as cancelled.')) return;
    setCancelling(true);
    try {
      await api('/api/events/' + id, { method: 'DELETE' });
      if (onBack) onBack();
      else nav(backTo || '/reader-events');
    } catch (e) {
      alert(errMsg(e));
      setCancelling(false);
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
  if (!ev) {
    return (
      <div className="content">
        <main aria-live="polite">
          <div className="empty">Loading event…</div>
        </main>
        <aside />
      </div>
    );
  }

  const chatBlock = (
    <>
      <div className="chatbox">
        <h3>
          💬 Event Chat{' '}
          <span
            className="countpill"
            style={{ background: 'var(--soft)', borderRadius: 999, fontSize: 12, padding: '2px 10px' }}
          >
            {msgs.count || 0}
          </span>
        </h3>
        <div className="chat-messages" ref={chatListRef}>
          {msgs.messages && msgs.messages.length > 0 ? (
            msgs.messages.map((m, i) => {
              const isOwn = !!(meName && m.author && m.author.toLowerCase() === meName.toLowerCase());
              return (
                <div className={'msg' + (isOwn ? ' own' : '')} key={i}>
                  <span className="msg-av">{initialsOf(m.author) || 'M'}</span>
                  <div className="msg-body">
                    <div className="msg-meta">
                      <b className="msg-author">{m.author || 'Member'}</b>
                      <span className="msg-time">
                        {m.createdAt
                          ? new Date(m.createdAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
                          : ''}
                      </span>
                    </div>
                    <div className="msg-bubble">
                      <p>{m.content}</p>
                    </div>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="empty" style={{ margin: 'auto' }}>
              No messages yet. Start the conversation!
            </div>
          )}
        </div>
        {isChatClosed ? (
          <div className="chat-closed-banner">
            🔒 Event chat is closed. Conversations were open for 48 hours following the event scheduled time.
          </div>
        ) : null}
        <div className={`chatbox-composer ${isChatClosed ? 'disabled' : ''}`}>
          <div className="composer-pill">
            <input
              placeholder={isChatClosed ? 'Chat is closed (ended 48h after the event)' : 'Type a message…'}
              aria-label="Type a message"
              disabled={isChatClosed}
              value={msgIn}
              onChange={(e) => setMsgIn(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !isChatClosed) void sendMsg();
              }}
            />
            <button
              className="composer-send"
              aria-label="Send"
              disabled={isChatClosed}
              onClick={() => {
                if (!isChatClosed) void sendMsg();
              }}
            >
              ➤
            </button>
          </div>
        </div>
      </div>
    </>
  );

  return (
    <div className={'content' + (isOrg ? ' solo' : '')}>
      <main aria-live="polite" style={{ minWidth: 0 }}>
        {onBack ? (
          <button
            className="backlink"
            type="button"
            style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer' }}
            onClick={onBack}
          >
            ← Back to Events
          </button>
        ) : (
          <Link
            className="backlink"
            to={backTo || '/reader-events'}
            onClick={(e) => {
              if (backTo) return;
              if (window.history.state && window.history.state.idx > 0) {
                e.preventDefault();
                nav(-1);
              }
            }}
          >
            ← Back to Events
          </Link>
        )}
        <div className="dhead">
          <img
            src={ev.coverImageUrl || FALLBACK}
            alt={'Cover of ' + (ev.title || 'event')}
            onError={(e) => {
              e.currentTarget.onerror = null;
              e.currentTarget.src = FALLBACK;
            }}
          />
          <div>
            <h2>{ev.title}</h2>
            <div style={{ fontWeight: 600 }}>👑 {ev.circleName || ''}</div>
            <div style={{ fontSize: 13, color: 'var(--muted)' }}>
              📅 {fmtDate(ev.eventDate)} &nbsp; 🕐 {fmtTime(ev.eventTime)} &nbsp; 📍 {placeOf(ev)}
            </div>
            <div className="tagrow" style={{ marginTop: 8 }}>
              {evTagList(ev).map((t, i) => (
                <span key={i}>{t}</span>
              ))}
            </div>
          </div>
          <div>{isReg ? <span className="statuspill accepted">✔ Registered</span> : null}</div>
        </div>
        {isOrg ? (
          <div className="mg-tabs" style={{ marginTop: 14 }}>
            <button className={mgView === 'overview' ? 'on' : ''} onClick={() => setMgView('overview')}>📋 Overview</button>
            <button className={mgView === 'event' ? 'on' : ''} onClick={() => setMgView('event')}>💬 Chat</button>
            <button className={mgView === 'edit' ? 'on' : ''} onClick={() => void openEdit()}>✎ Edit event</button>
            <button
              className={mgView === 'attendees' ? 'on' : ''}
              onClick={() => setMgView((v) => (v === 'attendees' ? 'overview' : 'attendees'))}
            >
              👥 View attendees
            </button>
            {ev.status === 'COMPLETED' ? (
              <button className={mgView === 'gallery' ? 'on' : ''} onClick={() => setMgView('gallery')}>
                🖼 Event Gallery
              </button>
            ) : null}
          </div>
        ) : null}
        {isOrg && mgView === 'overview' ? (
          <div className="ov">
            <div className="ov-stats">
              <div className="ov-stat">
                <span className="ic">📅</span>
                <div><b>{fmtDate(ev.eventDate)}</b><span>Date</span></div>
              </div>
              <div className="ov-stat">
                <span className="ic">🕐</span>
                <div><b>{fmtTime(ev.eventTime)}</b><span>Start time</span></div>
              </div>
              <div className="ov-stat">
                <span className="ic">📍</span>
                <div><b>{placeOf(ev) || '—'}</b><span>Location</span></div>
              </div>
              <div className="ov-stat">
                <span className="ic">👥</span>
                <div><b>{ev.registeredCount || 0} / {ev.capacity || '–'}</b><span>Registered</span></div>
              </div>
            </div>
            <div className="ov-grid">
              <div className="ov-col">
                <section className="panel">
                  <h3>📅 Event Details</h3>
                  <ul className="dlist">
                    <li><b>Event Name</b><span>{ev.title}</span></li>
                    <li><b>Reading Topic</b><span>{ev.readingTopic || ''}</span></li>
                    <li><b>Date</b><span>{fmtDate(ev.eventDate)}</span></li>
                    <li><b>Start Time</b><span>{fmtTime(ev.eventTime)}</span></li>
                    <li><b>Location</b><span>{ev.location || '—'}</span></li>
                    <li><b>Venue</b><span>{ev.venue || ''}</span></li>
                    <li><b>Capacity</b><span>{ev.capacity || '—'}</span></li>
                  </ul>
                </section>
                <section className="panel">
                  <h3>📄 About this event</h3>
                  <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>{ev.description || ''}</p>
                </section>
              </div>
              <div className="ov-col">
                <section className="panel">
                  <h3>👥 Organized by</h3>
                  <div className="req">
                    <img
                      src={circle.coverImageUrl || FALLBACK}
                      alt={'Cover of ' + (ev.circleName || 'circle')}
                      onError={(e) => {
                        e.currentTarget.onerror = null;
                        e.currentTarget.src = FALLBACK;
                      }}
                    />
                    <div>
                      <b>{ev.circleName || ''}</b>
                      <span>📚 {circle.memberCount || ''} members</span>
                    </div>
                  </div>
                  <button
                    className="ghostbtn"
                    style={{ width: '100%', border: '1px solid var(--burgundy)', borderRadius: 10, padding: 9, background: '#fff' }}
                    onClick={() => {
                      if (ev.circleId) nav('/reader-circles/circle/' + ev.circleId);
                    }}
                  >
                    View Circle →
                  </button>
                </section>
                {ev.status !== 'COMPLETED' ? (
                  <section className="panel ov-danger">
                    <h3>🚫 Cancel event</h3>
                    <p style={{ fontSize: 13, color: 'var(--muted)', margin: '0 0 12px' }}>
                      Registered members will see this event as cancelled. This cannot be undone.
                    </p>
                    <button className="btn danger-ghost" disabled={cancelling} onClick={() => void cancelEvent()}>
                      {cancelling ? 'Cancelling…' : 'Cancel event'}
                    </button>
                  </section>
                ) : null}
              </div>
            </div>
          </div>
        ) : isOrg && mgView === 'edit' ? (
          editData ? (
            <div className="modal inline">
              <EventForm
                circles={[]}
                fixedCircleId={editData.circleId}
                initial={editData}
                onClose={() => setMgView('overview')}
                onSaved={() => setTick((t) => t + 1)}
              />
            </div>
          ) : null
        ) : isOrg && mgView === 'attendees' && id ? (
          <div className="card">
            <AttendeesPanel eventId={id} title={ev.title || ''} />
          </div>
        ) : isOrg && mgView === 'gallery' && id ? (
          <EventGallery eventId={id} canUpload />
        ) : !isOrg && !isAdmin && ev.status === 'COMPLETED' && !isReg ? (
          <div className="card" style={{ padding: '36px 20px', textAlign: 'center', margin: '20px 0' }}>
            <div style={{ fontSize: 44, marginBottom: 12 }}>🔒</div>
            <h3 style={{ margin: '0 0 8px', fontSize: 18 }}>Event Not Registered</h3>
            <p style={{ color: 'var(--muted)', maxWidth: 440, margin: '0 auto 20px', fontSize: 13.5, lineHeight: 1.5 }}>
              You were not registered for this event. Event details, discussions, and gallery memories are only accessible to registered attendees.
            </p>
            <button
              className="cardbtn"
              style={{ margin: '0 auto', display: 'inline-flex' }}
              onClick={() => nav('/reader-events/past')}
            >
              ← Back to Past Events
            </button>
          </div>
        ) : !isOrg && ev.status === 'COMPLETED' && id ? (
          <>
            <div className="mg-tabs" style={{ marginTop: 14 }}>
              <button className={rView === 'chat' ? 'on' : ''} onClick={() => setRView('chat')}>
                💬 Chat
              </button>
              <button className={rView === 'gallery' ? 'on' : ''} onClick={() => setRView('gallery')}>
                🖼 Event Gallery
              </button>
            </div>
            {rView === 'gallery' ? <EventGallery eventId={id} canUpload={isOrg || isAdmin} /> : chatBlock}
          </>
        ) : (
          chatBlock
        )}
      </main>
      {!isOrg ? (
      <aside aria-label="Details">
        <section className="panel">
          <h3>📅 Event Details</h3>
          <ul className="dlist">
            <li><b>Event Name</b><span>{ev.title}</span></li>
            <li><b>Reading Topic</b><span>{ev.readingTopic || ''}</span></li>
            <li><b>Date</b><span>{fmtDate(ev.eventDate)}</span></li>
            <li><b>Start Time</b><span>{fmtTime(ev.eventTime)}</span></li>
            <li><b>Location</b><span>{ev.location || '—'}</span></li>
            <li><b>Venue</b><span>{ev.venue || ''}</span></li>
            <li><b>Capacity</b><span>{ev.capacity || '—'}</span></li>
          </ul>
        </section>
        <section className="panel">
          <h3>📄 About this event</h3>
          <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>{ev.description || ''}</p>
        </section>
        <section className="panel">
          <h3>👥 Organized by</h3>
          <div className="req">
            <img
              src={circle.coverImageUrl || FALLBACK}
              alt={'Cover of ' + (ev.circleName || 'circle')}
              onError={(e) => {
                e.currentTarget.onerror = null;
                e.currentTarget.src = FALLBACK;
              }}
            />
            <div>
              <b>{ev.circleName || ''}</b>
              <span>📚 {circle.memberCount || ''} members</span>
            </div>
          </div>
          <button
            className="ghostbtn"
            style={{ width: '100%', border: '1px solid var(--burgundy)', borderRadius: 10, padding: 9, background: '#fff' }}
            onClick={() => {
              if (ev.circleId) nav('/reader-circles/circle/' + ev.circleId);
            }}
          >
            View Circle →
          </button>
        </section>
        <section className="panel">
          <h3>👥 Attendees ({ev.registeredCount || 0})</h3>
          <div className="avatars">
            {(pv || []).slice(0, 7).map((a, i) =>
              a.avatarUrl ? (
                <span key={i}>
                  <img
                    src={a.avatarUrl}
                    alt=""
                    onError={(e) => {
                      e.currentTarget.onerror = null;
                      e.currentTarget.src = FALLBACK;
                    }}
                  />
                </span>
              ) : (
                <span key={i}>{initialsOf(a.name)}</span>
              ),
            )}
            <span>+{Math.max(0, Number(ev.registeredCount || 0) - 7)}</span>
          </div>
        </section>
      </aside>
      ) : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Organiser Events view (organiser login: All / Organised / Member /  */
/* Past tabs, Create Event, role-based cards)                          */
/* ------------------------------------------------------------------ */

type OrgTab = 'all' | 'organised' | 'member' | 'past';

const ORG_TABS: { k: OrgTab; label: string; to: string }[] = [
  { k: 'all', label: 'All Events', to: '/reader-events' },
  { k: 'organised', label: 'Organised Events', to: '/reader-events/organised' },
  { k: 'member', label: 'Registered Events', to: '/reader-events/member' },
  { k: 'past', label: 'Past Events', to: '/reader-events/past' },
];

function isOnlineEv(e: EventItem): boolean {
  return (
    String(e.mode || '').toUpperCase() === 'ONLINE' ||
    String(e.venue || '').toLowerCase().indexOf('online') >= 0
  );
}

function dayNum(iso?: string): string {
  return (iso || '').slice(8, 10) || '–';
}

function monShort(iso?: string): string {
  if (!iso || iso.length < 7) return '';
  const d = new Date(iso.slice(0, 10) + 'T00:00:00');
  if (isNaN(d.getTime())) return '';
  return d.toLocaleString(undefined, { month: 'short' }).toUpperCase();
}

function dowShort(iso?: string): string {
  if (!iso || iso.length < 10) return '';
  const d = new Date(iso.slice(0, 10) + 'T00:00:00');
  if (isNaN(d.getTime())) return '';
  return d.toLocaleString(undefined, { weekday: 'short' });
}

function AttendeesPanel({ eventId, title }: { eventId: string; title: string }) {
  const [list, setList] = useState<{ readerId?: string; fullName?: string; email?: string; registeredAt?: string; avatarUrl?: string; attended?: boolean }[]>([]);
  const [err, setErr] = useState('');
  useEffect(() => {
    let on = true;
    (async () => {
      try {
        const d = await api<{ readerId?: string; fullName?: string; email?: string; registeredAt?: string; avatarUrl?: string; attended?: boolean }[]>('/api/events/' + eventId + '/registrations');
        if (on) setList(d || []);
      } catch (e) {
        if (on) setErr(errMsg(e));
      }
    })();
    return () => {
      on = false;
    };
  }, [eventId]);
  return (
    <>
      <div className="headrow">
        <h3>👥 Attendees ({list.length})</h3>
      </div>
      <p className="sub">{list.length} member{list.length === 1 ? '' : 's'} registered for {title || 'this event'}.</p>
      {err ? <div className="merr">{err}</div> : null}
      {!list.length && !err ? (
        <div className="emptybox">No attendees yet.</div>
      ) : (
        <table className="tbl">
          <thead><tr><th>#</th><th>Name</th><th>Email</th><th>Registered</th></tr></thead>
          <tbody>
            {list.map((a, i) => (
              <tr key={String(a.readerId || i)}>
                <td>{i + 1}</td>
                <td>
                  <span className="who">
                    <span className="face">{a.avatarUrl ? <img src={a.avatarUrl} alt="" /> : initialsOf(a.fullName)}</span>
                    {a.fullName || 'Member'}
                  </span>
                </td>
                <td>{a.email || '—'}</td>
                <td>{a.registeredAt ? new Date(a.registeredAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}

function OrganizerEventsView({ tab, externalQ, setSync }: { tab: OrgTab; externalQ: string; setSync: (s: string) => void }) {
  const nav = useNavigate();
  const [qText, setQText] = useState('');
  const [f, setF] = useState<{ q: string; circle: string; types: string[]; loc: string; cities: string[]; sort: string }>(
    { q: '', circle: '', types: [], loc: '', cities: [], sort: 'recent' },
  );
  const [circles, setCircles] = useState<Circle[]>([]);
  const [orgIds, setOrgIds] = useState<Record<string, number>>({});
  const [memIds, setMemIds] = useState<Record<string, number>>({});
  const [regMap, setRegMap] = useState<Record<string, Registration>>({});
  const [base, setBase] = useState<EventItem[]>([]);
  const [previews, setPreviews] = useState<Record<string, Attendee[]>>({});
  const [descs, setDescs] = useState<Record<string, string>>({});
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState('');
  const [showCreate, setShowCreate] = useState(false);

  const fromPath = tab === 'all' ? '/reader-events' : '/reader-events/' + tab;

  useEffect(() => {
    setQText(externalQ || '');
  }, [externalQ]);

  useEffect(() => {
    const t = window.setTimeout(() => {
      setF((p) => (p.q === qText ? p : { ...p, q: qText }));
    }, 350);
    return () => window.clearTimeout(t);
  }, [qText]);

  useEffect(() => {
    let on = true;
    setSync('Syncing with admin data…');
    (async () => {
      try {
        const cs = await api<Circle[]>('/api/circles?sort=name_asc');
        const mine = await api<Membership[]>('/api/users/me/memberships');
        let org: Circle[] = [];
        try {
          org = await api<Circle[]>('/api/users/me/organized-circles');
        } catch {
          org = [];
        }
        let regs: Registration[] = [];
        try {
          regs = await api<Registration[]>('/api/users/me/registrations');
        } catch {
          regs = [];
        }
        const mem: Record<string, number> = {};
        (mine || []).forEach((m) => {
          if (m.status === 'APPROVED') mem[m.circleId] = 1;
        });
        const og: Record<string, number> = {};
        (org || []).forEach((c) => {
          if (c.id) og[c.id] = 1;
        });
        const rm: Record<string, Registration> = {};
        regs.forEach((r) => {
          rm[r.eventId] = r;
        });
        const inMine = (id?: string) => !!id && (!!og[id] || !!mem[id]);
        let all = await api<EventItem[]>('/api/events?scope=all');
        all = (all || []).filter(
          (e) => inMine(e.circleId) && e.status !== 'DRAFT' && e.status !== 'CANCELLED',
        );
        const pvMap: Record<string, Attendee[]> = {};
        const dsMap: Record<string, string> = {};
        await Promise.all(
          all.map(async (e) => {
            try {
              pvMap[e.id] = (await api<Attendee[]>('/api/events/' + e.id + '/attendee-preview')) || [];
            } catch {
              pvMap[e.id] = [];
            }
            try {
              const d = await api<{ description?: string }>('/api/events/' + e.id);
              if (d && d.description) dsMap[e.id] = d.description;
            } catch {
              /* description optional */
            }
          }),
        );
        if (!on) return;
        setCircles(cs || []);
        setMemIds(mem);
        setOrgIds(og);
        setRegMap(rm);
        setBase(all);
        setPreviews(pvMap);
        setDescs(dsMap);
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

  const cityByCircle = useMemo(() => {
    const m: Record<string, string> = {};
    circles.forEach((c) => {
      if (c.id && c.city) m[c.id] = c.city;
    });
    return m;
  }, [circles]);

  const myCircles = useMemo(
    () => circles.filter((c) => orgIds[c.id] || memIds[c.id]),
    [circles, orgIds, memIds],
  );

  const cityOpts = useMemo(() => {
    const s = new Set<string>();
    myCircles.forEach((c) => {
      if (c.city) s.add(c.city);
    });
    return Array.from(s).sort();
  }, [myCircles]);

  const orgEvents = useMemo(
    () => base.filter((e) => !!orgIds[e.circleId || ''] && e.status === 'PUBLISHED'),
    [base, orgIds],
  );
  const memEvents = useMemo(
    () =>
      base.filter(
        (e) => !orgIds[e.circleId || ''] && !!memIds[e.circleId || ''] && e.status === 'PUBLISHED',
      ),
    [base, orgIds, memIds],
  );
  const pastEvents = useMemo(() => base.filter((e) => e.status === 'COMPLETED'), [base]);
  const allEvents = useMemo(
    () => base.filter((e) => e.status === 'PUBLISHED' || e.status === 'COMPLETED'),
    [base],
  );

  const counts = { all: allEvents.length, organised: orgEvents.length, member: memEvents.length, past: pastEvents.length };
  const tabList = tab === 'organised' ? orgEvents : tab === 'member' ? memEvents : tab === 'past' ? pastEvents : allEvents;

  const shown = useMemo(() => {
    const q = f.q.trim().toLowerCase();
    const out = tabList.filter((e) => {
      if (f.circle && e.circleId !== f.circle) return false;
      const online = isOnlineEv(e);
      if (f.loc === 'ONLINE' && !online) return false;
      if (f.loc === 'OFFLINE' && online) return false;
      if (f.cities.length && f.cities.indexOf(cityByCircle[e.circleId || ''] || '') < 0) return false;
      if (q) {
        const hay = [e.title, e.venue, e.readingTopic, e.circleName].map((x) => String(x || '').toLowerCase()).join(' ');
        if (hay.indexOf(q) < 0) return false;
      }
      return true;
    });
    out.sort((a, b) => {
      if (f.sort === 'name') return String(a.title || '').localeCompare(String(b.title || ''));
      if (f.sort === 'soonest') return String(a.eventDate || '').localeCompare(String(b.eventDate || ''));
      const ca = a.createdAt || a.eventDate || '';
      const cb = b.createdAt || b.eventDate || '';
      return cb.localeCompare(ca);
    });
    return out;
  }, [tabList, f, cityByCircle]);

  const calDates = useMemo(() => {
    const m: Record<string, number> = {};
    allEvents.forEach((e) => {
      if (e.eventDate) m[String(e.eventDate).slice(0, 10)] = 1;
    });
    return m;
  }, [allEvents]);

  async function register(id: string) {
    setBusyId(id);
    try {
      await api('/api/events/' + id + '/register', { method: 'POST' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
    } finally {
      setBusyId('');
    }
  }

  function openDetail(id: string) {
    nav('/reader-events/event/' + id, { state: { from: fromPath } });
  }

  function orgCard(e: EventItem) {
    const isOrg = !!orgIds[e.circleId || ''];
    const registered = !!regMap[e.id] || !!e.isRegistered;
    const full = e.capacity != null && Number(e.registeredCount || 0) >= Number(e.capacity);
    const desc = descs[e.id] || '';
    return (
      <div className="ocard" key={e.id}>
        <div className="odate">
          <span className="odate-m">{monShort(e.eventDate)}</span>
          <b>{dayNum(e.eventDate)}</b>
          <span className="odate-d">{dowShort(e.eventDate)}</span>
        </div>
        <img
          className="ocover"
          src={e.coverImageUrl || FALLBACK}
          alt=""
          onError={(e2) => {
            e2.currentTarget.onerror = null;
            e2.currentTarget.src = FALLBACK;
          }}
        />
        <div className="omain">
          <div className="orow">
            <span className="opill">{e.circleName || ''}</span>
            {isOrg ? (
              <span className="rolebadge org">👑 Organizer</span>
            ) : (
              <span className="rolebadge mem">👤 Member</span>
            )}
          </div>
          <h3>{e.title}</h3>
          {desc ? <p className="odesc">{desc}</p> : null}
          <div className="ometa">
            <span className="m">📅 {fmtDate(e.eventDate)}</span>
            <span className="m">🕐 {fmtTime(e.eventTime)}</span>
            <span className="m">📍 {placeOf(e) || '—'}</span>
          </div>
          <div className="ofoot">
            <div className="oreg">
              <Faces preview={previews[e.id] || []} total={Number(e.registeredCount || 0)} />
              <span>
                <b>{e.registeredCount || 0}</b>
                {e.capacity ? ` / ${e.capacity}` : ''} registered
              </span>
            </div>
            {evTagList(e).length ? (
              <div className="otags">
                {evTagList(e).map((t, i) => (
                  <span key={i}>{t}</span>
                ))}
              </div>
            ) : null}
          </div>
        </div>
        <div className="oactions">
          {isOrg ? (
            <button className="oabtn primary" onClick={() => openDetail(e.id)}>🛠️&nbsp;&nbsp;Manage Event</button>
          ) : registered ? (
            <button className="oabtn primary" onClick={() => openDetail(e.id)}>👁&nbsp;&nbsp;View Event</button>
          ) : full ? (
            <button className="oabtn" disabled>🚫 Event Full</button>
          ) : (
            <button className="oabtn primary" disabled={busyId === e.id} onClick={() => void register(e.id)}>
              {busyId === e.id ? 'Registering…' : '🧍‍♂️  Register for Event'}
            </button>
          )}
        </div>
      </div>
    );
  }

  const onlineOn = f.loc === 'ONLINE';

  return (
    <div className="content">
      <main aria-live="polite" style={{ minWidth: 0 }}>
        <div className="orghead">
          <div>
            <h1 className="page">Events</h1>
            <p className="sub">Discover and manage events from the circles you are part of.</p>
          </div>
          <button className="createbtn" onClick={() => setShowCreate(true)}>＋ Create Event</button>
        </div>
        <div className="tabs" role="tablist">
          {ORG_TABS.map((t) => (
            <button key={t.k} className={tab === t.k ? 'on' : ''} onClick={() => nav(t.to)}>
              {t.label} <span className="cbadge">{counts[t.k]}</span>
            </button>
          ))}
        </div>
        <div className="toolbar">
          <div className="search">
            <span className="lens">🔍</span>
            <input type="search" placeholder="Search events…" value={qText} onChange={(e) => setQText(e.target.value)} />
          </div>
          <select value={f.circle} onChange={(e) => setF((p) => ({ ...p, circle: e.target.value }))}>
            <option value="">All Circles</option>
            {myCircles.map((x) => (
              <option key={x.id} value={x.id}>{x.name}</option>
            ))}
          </select>
          <select value={f.loc} onChange={(e) => setF((p) => ({ ...p, loc: e.target.value }))}>
            <option value="">All Locations</option>
            <option value="ONLINE">Online</option>
            <option value="OFFLINE">Offline</option>
          </select>
          <select value={f.sort} onChange={(e) => setF((p) => ({ ...p, sort: e.target.value }))}>
            <option value="recent">Most Recent</option>
            <option value="soonest">Soonest First</option>
            <option value="name">Name A–Z</option>
          </select>
        </div>
        {err ? (
          <div className="err">{err}</div>
        ) : !shown.length ? (
          <div className="empty">No {tab === 'all' ? '' : tab + ' '}events.</div>
        ) : (
          <div className="olist">{shown.map((e) => orgCard(e))}</div>
        )}
      </main>
      <aside aria-label="Details">
        <section className="panel">
          <h3>📅 Event Calendar</h3>
          <MonthCal dates={calDates} />
        </section>
        <section className="panel">
          <h3>🔻 Filters</h3>
          <div className="frow">
            <b>Location</b>
            <label className="check">
              <input
                type="checkbox"
                checked={onlineOn}
                onChange={() => setF((p) => ({ ...p, loc: p.loc === 'ONLINE' ? '' : 'ONLINE' }))}
              />{' '}
              Online
            </label>
            {cityOpts.map((c) => (
              <label className="check" key={c}>
                <input
                  type="checkbox"
                  checked={f.cities.indexOf(c) >= 0}
                  onChange={() => {
                    setF((p) => ({
                      ...p,
                      cities: p.cities.indexOf(c) >= 0 ? p.cities.filter((x) => x !== c) : [...p.cities, c],
                    }));
                  }}
                />{' '}
                {c}
              </label>
            ))}
          </div>
          <div className="frow">
            <b>Circle</b>
            <select value={f.circle} onChange={(e) => setF((p) => ({ ...p, circle: e.target.value }))}>
              <option value="">All Circles</option>
              {myCircles.map((x) => (
                <option key={x.id} value={x.id}>{x.name}</option>
              ))}
            </select>
          </div>
        </section>
      </aside>
      {showCreate ? (
        <EventModal
          circles={myCircles.filter((c) => orgIds[c.id]).map((c) => ({ id: c.id, name: c.name }))}
          onClose={() => setShowCreate(false)}
          onSaved={() => {
            setShowCreate(false);
            setTick((t) => t + 1);
          }}
        />
      ) : null}
    </div>
  );
}

export default function ReaderEvents() {
  const [sync, setSync] = useState('Connecting…');
  const [isOrg, setIsOrg] = useState<boolean | null>(null);

  useEffect(() => {
    let on = true;
    api<Circle[]>('/api/users/me/organized-circles')
      .then((o) => {
        if (on) setIsOrg((o || []).length > 0);
      })
      .catch(() => {
        if (on) setIsOrg(false);
      });
    return () => {
      on = false;
    };
  }, []);

  return (
    <ReaderLayout
      active="events"
      syncNote={sync}
    >
      {isOrg ? (
        <Routes>
          <Route index element={<OrganizerEventsView tab="all" externalQ="" setSync={setSync} />} />
          <Route path="organised" element={<OrganizerEventsView tab="organised" externalQ="" setSync={setSync} />} />
          <Route path="member" element={<OrganizerEventsView tab="member" externalQ="" setSync={setSync} />} />
          <Route path="past" element={<OrganizerEventsView tab="past" externalQ="" setSync={setSync} />} />
          <Route path="event/:id" element={<EventDetail setSync={setSync} />} />
        </Routes>
      ) : (
        <Routes>
          <Route index element={<EventList tab="upcoming" externalQ="" setSync={setSync} />} />
          <Route path="registered" element={<EventList tab="registered" externalQ="" setSync={setSync} />} />
          <Route path="past" element={<EventList tab="past" externalQ="" setSync={setSync} />} />
          <Route path="event/:id" element={<EventDetail setSync={setSync} />} />
        </Routes>
      )}
    </ReaderLayout>
  );
}
