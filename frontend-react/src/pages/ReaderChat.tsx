import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api, ApiError, initialsOf, resolveMedia } from '../lib/api';
import './ReaderChat.css';

type Membership = {
  status?: string;
  circleId?: string | number;
  circleName?: string;
};

type CircleApi = {
  id?: string | number;
  name?: string;
  memberCount?: number;
  coverImageUrl?: string;
};

type Circle = {
  id: string;
  name: string;
  memberCount: number;
  cover?: string;
  lounge?: boolean;
};

type LoungeApi = {
  circleId?: string;
  name?: string;
  memberCount?: number;
};

type ChatMessage = {
  senderId?: string | number;
  senderName?: string;
  senderAvatarUrl?: string;
  sentAt?: string;
  content?: string;
  deleted?: boolean;
};

type MeShape = {
  id?: string | number;
  name?: string;
  email?: string;
};

function fmtTime(iso?: string): string {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  } catch {
    return '';
  }
}

export default function ReaderChat() {
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [circles, setCircles] = useState<Circle[]>([]);
  const [lounge, setLounge] = useState<Circle | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [meId, setMeId] = useState<string | null>(null);
  const [msgs, setMsgs] = useState<ChatMessage[]>([]);
  const [q, setQ] = useState('');
  const [draft, setDraft] = useState('');
  const [gchatErr, setGchatErr] = useState('');
  const [pageErr, setPageErr] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [sync, setSync] = useState('Connecting…');
  const [showChannels, setShowChannels] = useState(false);
  const msgsEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (msgsEndRef.current) {
      msgsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [msgs]);

  const goAuth = useCallback(
    (e: unknown) => {
      if (e instanceof ApiError && e.status === 401) {
        nav('/auth');
        return true;
      }
      return false;
    },
    [nav]
  );

  const loadMessages = useCallback(
    async (circleId: string, silent: boolean, curMeId: string | null, showErr: (m: string) => void) => {
      try {
        if (!silent) showErr('');
        const m = await api<ChatMessage[]>(`/api/circles/${circleId}/messages?size=50`);
        const rows = (Array.isArray(m) ? m : []).filter((x) => !x.deleted);
        setMsgs(rows);
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) {
          nav('/auth');
          return;
        }
        if (!silent) showErr(e instanceof Error ? e.message : 'Failed to load messages');
      }
      void curMeId;
    },
    [nav]
  );

  useEffect(() => {
    let on = true;
    const showErr = (m: string) => {
      if (on) setGchatErr(m);
    };
    (async () => {
      setSync('Syncing…');
      try {
        const me = await api<MeShape>('/api/users/me');
        if (!on) return;
        setMeId(me.id != null ? String(me.id) : null);
      } catch {
        /* ignore me failure, matches original */
      }
      try {
        const mine = await api<Membership[]>('/api/users/me/memberships');
        const approved = (mine || []).filter((m) => m.status === 'APPROVED');
        const all = await api<CircleApi[]>('/api/circles?sort=name_asc');
        const byId: Record<string, CircleApi> = {};
        (all || []).forEach((c) => {
          if (c.id != null) byId[String(c.id)] = c;
        });
        const mapped: Circle[] = approved.map((m) => {
          const c = m.circleId != null ? byId[String(m.circleId)] || {} : {};
          return {
            id: String(m.circleId),
            name: m.circleName || c.name || 'Circle',
            memberCount: c.memberCount != null ? c.memberCount : 1,
            cover: c.coverImageUrl,
          };
        });
        // Organisers have no membership row for their own circles, but the
        // backend lets them read/post chat there — merge those in too.
        let org: CircleApi[] = [];
        try {
          org = await api<CircleApi[]>('/api/users/me/organized-circles');
        } catch {
          org = [];
        }
        const seen = new Set(mapped.map((c) => c.id));
        (org || []).forEach((c) => {
          if (c.id == null || seen.has(String(c.id))) return;
          seen.add(String(c.id));
          const full = byId[String(c.id)] || {};
          mapped.push({
            id: String(c.id),
            name: c.name || full.name || 'Circle',
            memberCount: full.memberCount != null ? full.memberCount : c.memberCount != null ? c.memberCount : 1,
            cover: full.coverImageUrl || c.coverImageUrl,
          });
        });
        if (!on) return;
        // Permanent Organisers Lounge: this call also syncs membership on the
        // backend (promoted organisers are added here). Readers get no lounge.
        // Fetched first so the lounge circle can be kept out of MY CIRCLES.
        let loungeEntry: Circle | null = null;
        try {
          const ch = await api<{ lounge?: LoungeApi }>('/api/chat/channels');
          const lg = ch ? ch.lounge : undefined;
          if (lg && lg.circleId) {
            loungeEntry = {
              id: String(lg.circleId),
              name: lg.name || 'Organisers Lounge',
              memberCount: Number(lg.memberCount || 0),
              lounge: true,
            };
            if (!on) return;
            setLounge(loungeEntry);
          }
        } catch {
          /* no lounge for this role; ignore */
        }
        if (loungeEntry) {
          const lid = String(loungeEntry.id);
          for (let i = mapped.length - 1; i >= 0; i--) {
            if (String(mapped[i].id) === lid) mapped.splice(i, 1);
          }
        }
        setCircles(mapped);
        setLoaded(true);
        if (!mapped.length && !loungeEntry) {
          setSync('Synced');
          return;
        }
        let want: string | null = null;
        try {
          want = params.get('circle');
        } catch {
          want = null;
        }
        let first: Circle = loungeEntry || mapped[0];
        if (want) {
          const all = loungeEntry ? [loungeEntry, ...mapped] : mapped;
          const f = all.find((c) => String(c.id) === String(want));
          if (f) first = f;
        }
        setSelected(String(first.id));
        try {
          const m = await api<ChatMessage[]>(`/api/circles/${first.id}/messages?size=50`);
          if (!on) return;
          setMsgs((Array.isArray(m) ? m : []).filter((x) => !x.deleted));
        } catch (e) {
          if (!on) return;
          if (e instanceof ApiError && e.status === 401) {
            nav('/auth');
            return;
          }
          showErr('');
        }
        setSync('Synced with admin just now');
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) {
          nav('/auth');
          return;
        }
        setPageErr(e instanceof Error ? e.message : 'Failed to load circles');
        setSync('Offline');
        setLoaded(true);
      }
    })();
    return () => {
      on = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selected) return;
    let on = true;
    const tick = async () => {
      try {
        const m = await api<ChatMessage[]>(`/api/circles/${selected}/messages?size=50`);
        if (on) setMsgs((Array.isArray(m) ? m : []).filter((x) => !x.deleted));
      } catch {
        /* silent poll, matches original */
      }
    };
    const id = window.setInterval(tick, 6000);
    return () => {
      on = false;
      window.clearInterval(id);
    };
  }, [selected]);

  async function select(id: string) {
    setSelected(id);
    setShowChannels(false);
    setGchatErr('');
    try {
      await loadMessages(
        id,
        true,
        meId,
        (m) => setGchatErr(m)
      );
    } catch {
      /* handled inside */
    }
  }

  async function send() {
    const v = draft.trim();
    if (!v || !selected) return;
    const circleId = selected;
    setDraft('');
    try {
      await api(`/api/circles/${circleId}/messages`, { method: 'POST', body: JSON.stringify({ content: v }) });
      await loadMessages(circleId, true, meId, (m) => setGchatErr(m));
    } catch (e) {
      if (goAuth(e)) return;
      // Demoted mid-session: the lounge is revoked server-side (403). Drop it
      // locally so it disappears immediately instead of lingering.
      if (e instanceof ApiError && e.status === 403 && lounge != null && String(selected) === String(lounge.id)) {
        setLounge(null);
        setGchatErr('This channel is reserved for organisers and admins.');
        return;
      }
      setGchatErr(e instanceof Error ? e.message : 'Send failed');
      setDraft(v);
    }
  }

  const filtered = useMemo(() => {
    const needle = q.toLowerCase();
    return circles.filter((c) => !needle || (c.name || '').toLowerCase().indexOf(needle) >= 0);
  }, [circles, q]);

  const cur = (lounge && String(selected) === String(lounge.id))
    ? lounge
    : circles.find((x) => String(x.id) === String(selected)) || null;
  const isLounge = !!cur?.lounge;
  const headName = !loaded ? '—' : cur ? String(cur.name).toUpperCase() : 'No circles';
  const headSub = !loaded
    ? 'Select a circle'
    : cur
      ? isLounge
        ? `${cur.memberCount}${cur.memberCount === 1 ? ' member' : ' members'} • Organisers & Admin Exclusive`
        : `${cur.memberCount}${cur.memberCount === 1 ? ' member' : ' members'}`
      : 'Join a circle first';
  const headAv = isLounge ? '👑' : cur ? initialsOf(cur.name) || 'C' : 'C';

  return (
    <ReaderLayout active="chat" syncNote={sync}>
      <div className="gchat-wrap">
        {pageErr && <div className="err">{pageErr}</div>}
        <div className="gchat-panel">
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
              <p>Talk books and ideas with your circles. Only circles you joined appear here.</p>
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
          <aside aria-label="My circles">
            <div className={'gchat-left' + (showChannels ? ' mobile-open' : '')}>
              <button
                type="button"
                className="gchat-mobile-close"
                onClick={() => setShowChannels(false)}
              >
                ✕ Close Circles
              </button>
              <div className="gchat-search">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <circle cx="11" cy="11" r="7" />
                  <path d="m20 20-3.2-3.2" />
                </svg>
                <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search my circles..." aria-label="Search my circles" />
              </div>
              {lounge && (
                <>
                  <div className="gchat-seclbl gold">ORGANISERS &amp; ADMIN</div>
                  <button
                    type="button"
                    className={'gchat-chan lounge' + (selected != null && String(selected) === String(lounge.id) ? ' on' : '')}
                    onClick={() => select(String(lounge.id))}
                  >
                    <span className="gchat-badge gold">👑</span>
                    <span>
                      <b>{String(lounge.name).toUpperCase()}</b>
                      <small>
                        {lounge.memberCount} {lounge.memberCount === 1 ? 'member' : 'members'} • Leadership
                      </small>
                    </span>
                    {selected != null && String(selected) === String(lounge.id) ? <span className="gchat-tick">✓</span> : <span className="gchat-tick" style={{ visibility: 'hidden' }}>✓</span>}
                  </button>
                </>
              )}
              <div className="gchat-seclbl">MY CIRCLES</div>
              <div id="gchatList" style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {filtered.map((c) => {
                  const isOn = selected != null && String(selected) === String(c.id);
                  return (
                    <button key={String(c.id)} type="button" className={'gchat-chan' + (isOn ? ' on' : '')} onClick={() => select(String(c.id))}>
                      <span className="gchat-badge">{initialsOf(c.name) || 'C'}</span>
                      <span>
                        <b>{String(c.name).toUpperCase()}</b>
                        <small>
                          {c.memberCount} {c.memberCount === 1 ? 'member' : 'members'}
                        </small>
                      </span>
                      {isOn ? <span className="gchat-tick">✓</span> : <span className="gchat-tick" style={{ visibility: 'hidden' }}>✓</span>}
                    </button>
                  );
                })}
                {loaded && filtered.length === 0 && circles.length > 0 && (
                  <p style={{ color: 'var(--muted)', fontSize: 13 }}>No matches.</p>
                )}
              </div>
            </div>
          </aside>
          <div className="gchat-mid" aria-label="Conversation">
            <div className="gchat-head">
              <span className={'gchat-badge' + (isLounge ? ' gold' : '')}>{headAv}</span>
              <div className="gchat-head-info">
                <h3>{headName}</h3>
                <small>{headSub}</small>
              </div>
              <button
                type="button"
                className="gchat-mobile-toggle"
                onClick={() => setShowChannels(!showChannels)}
                aria-label="Toggle circles list"
              >
                💬 Circles ▾
              </button>
              <span className="gchat-dot" title="Connected"></span>
            </div>
            <div className="gchat-msgs" aria-live="polite">
              {loaded && circles.length === 0 && !lounge ? (
                <div className="gchat-empty-state">
                  <div className="gchat-empty-icon">👥</div>
                  <h4>No circles yet</h4>
                  <p>Join a circle from Circles page — approved circles appear here for chat.</p>
                </div>
              ) : msgs.length ? (
                msgs.map((x, i) => {
                  const own = meId != null && x.senderId != null && String(x.senderId) === String(meId);
                  return (
                    <div key={i} className={'gchat-row' + (own ? ' own' : '')}>
                      <span className="gchat-av">
                        {x.senderAvatarUrl ? (
                          <img src={resolveMedia(x.senderAvatarUrl)} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                        ) : (
                          initialsOf(x.senderName) || 'C'
                        )}
                      </span>
                      <div>
                        <div className="gchat-meta">
                          <b>{x.senderName || 'Member'}</b>
                          <span>{fmtTime(x.sentAt)}</span>
                        </div>
                        <div className="gchat-bub">
                          <p>{x.content || ''}</p>
                        </div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="gchat-empty-state">
                  <div className={'gchat-empty-icon' + (isLounge ? ' gold' : '')}>{isLounge ? '👑' : '💬'}</div>
                  <h4>No messages yet</h4>
                  <p>{isLounge ? 'Welcome to the Organisers Lounge. Say hello to start the discussion!' : 'Say hello and start the conversation in this circle!'}</p>
                </div>
              )}
              <div ref={msgsEndRef} />
            </div>
            {gchatErr && (
              <div className="err" style={{ margin: '8px 16px 0' }}>
                {gchatErr}
              </div>
            )}
            <div className="gchat-comp">
              <div className="gchat-pill">
                <button
                  type="button"
                  className="gchat-tool"
                  title="Add emoji"
                  onClick={() => setDraft((d) => d + ' 😊')}
                >
                  😊
                </button>
                <input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void send();
                  }}
                  placeholder={cur ? (isLounge ? 'Message organisers & admin...' : 'Type a message...') : 'Join a circle to chat...'}
                  maxLength={2000}
                  aria-label="Type a message"
                  autoComplete="off"
                  disabled={!cur}
                />
                <button className="gchat-send" type="button" title="Send message" disabled={!cur || !draft.trim()} onClick={() => void send()}>
                  ➤
                </button>
              </div>
            </div>
          </div>
          <aside className="gchat-right" aria-label="Circle information">
            <div className="gchat-card">
              <h3>{isLounge ? 'Organisers Group' : 'Circle Information'}</h3>
              <div className="gchat-big">{cur ? initialsOf(cur.name) || 'C' : 'C'}</div>
              <p className="gchat-cname">{cur ? String(cur.name).toUpperCase() : '—'}</p>
              <p className="gchat-csub">{cur ? `${cur.memberCount}${cur.memberCount === 1 ? ' member' : ' members'}` : '—'}</p>
              <div className="gchat-stat">
                <span>Members</span>
                <b>{cur ? cur.memberCount : '–'}</b>
              </div>
            </div>
            <div className="gchat-card">
              <h3>Circle Guidelines</h3>
              <ul className="gchat-rules">
                <li>
                  <span className="gchat-ok">✓</span>
                  <span>Keep the conversation respectful and friendly.</span>
                </li>
                <li>
                  <span className="gchat-ok">✓</span>
                  <span>Avoid spoilers (use spoiler tags if needed).</span>
                </li>
                <li>
                  <span className="gchat-ok">✓</span>
                  <span>Stay on topic and relevant to the circle.</span>
                </li>
                <li>
                  <span className="gchat-ok">✓</span>
                  <span>No hate speech or personal attacks.</span>
                </li>
              </ul>
            </div>
          </aside>
        </div>
        </div>
      </div>
    </ReaderLayout>
  );
}
