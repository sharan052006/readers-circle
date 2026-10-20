import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, clearTokens, getAccess, setApiBase, apiBase, type Me } from '../../lib/api';
import { EventModal } from '../ManageEvents';
import { EventDetail } from '../ReaderEvents';
import { COVER_PLACEHOLDER } from '../../lib/placeholders';
import '../CircleManage.css';
import '../ReaderEvents.css';
import './AdminEvents.css';
import AdminLayout from '../../components/AdminLayout';

function errMsg(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Request failed';
}

type Circle = { id: string; name?: string; category?: string; hidden?: boolean };
type Ev = {
  id: string;
  title?: string;
  readingTopic?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  circleId?: string;
  circleName?: string;
  coverImageUrl?: string;
  registeredCount?: number;
  capacity?: number;
  status?: string;
  description?: string;
};

type Bucket = 'upcoming' | 'past' | 'cancelled';
const PAGE = 8;

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function bucket(e: Ev): Bucket {
  if (e.status === 'CANCELLED') return 'cancelled';
  if (e.status === 'COMPLETED' || (e.eventDate && e.eventDate < todayStr())) return 'past';
  return 'upcoming';
}

function parseEventDate(dateStr?: string) {
  if (!dateStr) return { day: '12', monthYear: 'OCT 2026', weekday: 'SUN' };
  try {
    const raw = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
    const parts = raw.split('-');
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      const d = new Date(year, month, day);
      const monthName = d.toLocaleString('en-US', { month: 'short' }).toUpperCase();
      const weekday = d.toLocaleString('en-US', { weekday: 'short' }).toUpperCase();
      return {
        day: String(day),
        monthYear: `${monthName} ${year}`,
        weekday: weekday,
      };
    }
  } catch {}
  return { day: dateStr.slice(-2), monthYear: dateStr.slice(0, 7), weekday: '' };
}

export default function AdminEvents() {
  const navigate = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [err, setErr] = useState('');
  const [baseUrl, setBaseUrl] = useState(apiBase());

  const [all, setAll] = useState<Ev[]>([]);
  const [tab, setTab] = useState<Bucket>('upcoming');
  const [page, setPage] = useState(1);
  const [circles, setCircles] = useState<Circle[]>([]);

  const [q, setQ] = useState('');
  const [fCircle, setFCircle] = useState('all');
  const [fSort, setFSort] = useState('date_desc');

  const [modal, setModal] = useState(false);
  const [viewId, setViewId] = useState<string | null>(null);
  const [initialView, setInitialView] = useState<'overview' | 'event' | 'gallery'>('overview');
  const [openMenu, setOpenMenu] = useState<string | null>(null);

  useEffect(() => {
    let on = true;
    (async () => {
      if (!getAccess()) {
        navigate('/auth?redirect=/events');
        return;
      }
      try {
        const m = await api<Me>('/api/users/me');
        if (!on) return;
        if (m.role !== 'ADMIN') {
          setErr('Signed in as ' + m.role + ' — this page is admin-only.');
          return;
        }
        setMe(m);
        const cs = await api<Circle[]>('/api/circles?sort=name_asc');
        if (!on) return;
        // Organisers Lounge is a hidden chat group, not a circle.
        setCircles(Array.isArray(cs) ? cs.filter((c) => !c.hidden) : []);
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) clearTokens();
        navigate('/auth?redirect=/events');
      }
    })();
    return () => {
      on = false;
    };
  }, [navigate]);

  const load = useCallback(async () => {
    try {
      setErr('');
      const p = new URLSearchParams({ search: q, circleId: fCircle, sort: fSort });
      const data = await api<Ev[]>('/api/events?' + p.toString());
      setAll(Array.isArray(data) ? data : []);
      setPage(1);
    } catch (e) {
      setErr(errMsg(e));
    }
  }, [q, fCircle, fSort]);

  useEffect(() => {
    if (me) void load();
  }, [me, load]);

  const counts = {
    upcoming: all.filter((e) => bucket(e) === 'upcoming').length,
    past: all.filter((e) => bucket(e) === 'past').length,
    cancelled: all.filter((e) => bucket(e) === 'cancelled').length,
  };
  const rows = all.filter((e) => bucket(e) === tab);
  const pages = Math.max(1, Math.ceil(rows.length / PAGE));
  const cur = Math.min(page, pages);
  const slice = rows.slice((cur - 1) * PAGE, cur * PAGE);

  async function setStatus(id: string, status: string) {
    try {
      await api('/api/events/' + id, { method: 'PATCH', body: JSON.stringify({ status }) });
      await load();
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  function openView(id: string, view: 'overview' | 'event' | 'gallery' = 'overview') {
    setInitialView(view);
    setViewId(id);
  }

  return (
    <>
      <AdminLayout
        activeView="events"
        baseUrl={baseUrl}
        setBaseUrl={(url) => {
          setBaseUrl(url);
          setApiBase(url.replace(/\/+$/, ''));
        }}
        me={me}
        signOut={() => {
          clearTokens();
          navigate('/auth');
        }}
        className="admin-events-app"
      >
        {viewId ? (
          <EventDetail
            setSync={() => {}}
            eventId={viewId}
            asOrganizer
            initialView={initialView}
            onBack={() => {
              setViewId(null);
              setInitialView('overview');
              void load();
            }}
          />
        ) : (
          <div>
            {err && <div className="err">{err}</div>}
          <div className="headrow">
            <div>
              <h1 className="page">EVENTS</h1>
              <p className="sub">Manage and monitor events across all reading circles.</p>
            </div>
            <span className="sp" style={{ flex: 1 }}></span>
            <button className="btn" onClick={() => setModal(true)}>
              + Create New Event
            </button>
          </div>
          <div className="toolbar">
            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search events by name, circle, location..." />
            <select value={fCircle} onChange={(e) => setFCircle(e.target.value)}>
              <option value="all">All Circles</option>
              {circles.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <select value={fSort} onChange={(e) => setFSort(e.target.value)}>
              <option value="date_desc">Date (Newest First)</option>
              <option value="date_asc">Date (Oldest First)</option>
              <option value="newest">Recently Created</option>
              <option value="name">Name A-Z</option>
            </select>
          </div>
          <div className="etabs">
            <button data-t="upcoming" className={tab === 'upcoming' ? 'on' : ''} onClick={() => { setTab('upcoming'); setPage(1); }}>
              Upcoming Events ({counts.upcoming})
            </button>
            <button data-t="past" className={tab === 'past' ? 'on' : ''} onClick={() => { setTab('past'); setPage(1); }}>
              Past Events ({counts.past})
            </button>
            <button data-t="cancelled" className={tab === 'cancelled' ? 'on' : ''} onClick={() => { setTab('cancelled'); setPage(1); }}>
              Cancelled Events ({counts.cancelled})
            </button>
          </div>
          <div>
            {!slice.length ? (
              <div className="empty">
                <span className="ic">📅</span>
                <b>{tab === 'upcoming' ? 'NO UPCOMING EVENTS' : tab === 'past' ? 'NO PAST EVENTS' : 'NO CANCELLED EVENTS'}</b>
                No events match the current filters.
              </div>
            ) : (
              slice.map((e) => {
                const dateInfo = parseEventDate(e.eventDate);
                const circleObj = circles.find((x) => x.id === e.circleId);
                const circleLabel = e.circleName || circleObj?.name || 'Tamil Literature Circle';
                const topicLabel = e.readingTopic || circleObj?.category || 'Tamil Literature';
                const venueLabel = [e.location, e.venue].filter(Boolean).join(' · ') || 'Online (Google Meet)';
                const timeLabel = (e.eventTime || '17:00').slice(0, 5);
                const capStr = `${e.registeredCount ?? 2} / ${e.capacity ?? 50}`;
                const descText =
                  e.description ||
                  `A discussion on ${e.title || 'the selected reading'} — its characters, themes, and relevance in today's world. All readers are welcome to join.`;

                return (
                  <div className="ev-card" key={e.id}>
                    <div className="ev-datebox">
                      <span className="ev-datebox-my">{(dateInfo.monthYear || '').split(' ')[0]}</span>
                      <span className="ev-datebox-day">{dateInfo.day}</span>
                      <span className="ev-datebox-dow">{dateInfo.weekday}</span>
                    </div>

                    <img
                      className="ev-cover-img"
                      alt={'Cover of ' + (e.title || 'event')}
                      src={e.coverImageUrl || COVER_PLACEHOLDER}
                      onError={(ev) => {
                        (ev.currentTarget as HTMLImageElement).src = COVER_PLACEHOLDER;
                      }}
                    />

                    <div className="ev-main">
                      <div className="ev-row">
                        <span className="ev-circle-pill">{circleLabel}</span>
                      </div>
                      <h3 className="ev-title">{e.title}</h3>
                      <div className="ev-meta-row">
                        <span className="ev-meta-item">
                          <span>📖</span>
                          <span>{topicLabel}</span>
                        </span>
                        <span className="ev-meta-item">
                          <span>📍</span>
                          <span>{venueLabel}</span>
                        </span>
                        <span className="ev-meta-item">
                          <span>🕒</span>
                          <span>{timeLabel}</span>
                        </span>
                        <span className="ev-meta-item">
                          <span>👥</span>
                          <span>{capStr}</span>
                        </span>
                      </div>
                      <p className="ev-desc">{descText}</p>
                    </div>

                    <div className="ev-side-actions">
                      <div className="ev-top-actions">
                        {tab === 'upcoming' ? (
                          <span className="pill">Upcoming</span>
                        ) : tab === 'past' ? (
                          <span className="pill past">Past</span>
                        ) : (
                          <span className="pill cancel">Cancelled</span>
                        )}
                        <div className="menuwrap">
                          <button
                            className="ev-dots-btn"
                            type="button"
                            onClick={() => setOpenMenu(openMenu === e.id ? null : e.id)}
                            aria-label="Event options"
                          >
                            •••
                          </button>
                          <span className={'menu' + (openMenu === e.id ? ' open' : '')}>
                            <button
                              onClick={() => {
                                setOpenMenu(null);
                                openView(e.id, 'event');
                              }}
                            >
                              💬 View Chat
                            </button>
                            {tab === 'past' || e.status === 'COMPLETED' ? (
                              <button
                                onClick={() => {
                                  setOpenMenu(null);
                                  openView(e.id, 'gallery');
                                }}
                              >
                                🖼 View Gallery
                              </button>
                            ) : null}
                            <button
                              onClick={() => {
                                setOpenMenu(null);
                                void setStatus(e.id, 'COMPLETED');
                              }}
                            >
                              Mark completed
                            </button>
                            <button
                              onClick={() => {
                                setOpenMenu(null);
                                void setStatus(e.id, 'CANCELLED');
                              }}
                            >
                              Cancel event
                            </button>
                          </span>
                        </div>
                      </div>
                      <button
                        className="ev-manage-btn"
                        type="button"
                        onClick={() => openView(e.id)}
                      >
                        👁 View Event
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
          <div className="footrow">
            <p className="sub">
              Showing {rows.length ? (cur - 1) * PAGE + 1 : 0}–{Math.min(rows.length, cur * PAGE)} of {rows.length} events
            </p>
            {pages > 1 && (
              <div className="pager">
                <button disabled={cur <= 1} onClick={() => setPage(cur - 1)}>
                  ‹
                </button>
                {Array.from({ length: pages }, (_, i) => i + 1).map((i) => (
                  <button key={i} className={i === cur ? 'on' : ''} onClick={() => setPage(i)}>
                    {i}
                  </button>
                ))}
                <button disabled={cur >= pages} onClick={() => setPage(cur + 1)}>
                  ›
                </button>
              </div>
            )}
          </div>
        </div>
        )}
      </AdminLayout>
      {modal && (
        <EventModal
          circles={circles.map((c) => ({ id: c.id, name: c.name || 'Circle' }))}
          initial={null}
          onClose={() => setModal(false)}
          onSaved={() => {
            setModal(false);
            void load();
          }}
        />
      )}
    </>
  );
}
