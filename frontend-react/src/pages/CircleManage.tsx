import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, apiBase, fmtDate, initialsOf, resolveMedia, type Me } from '../lib/api';
import { COVER_PLACEHOLDER } from '../lib/placeholders';
import { AttendeesView, EventChatView, EventDetailView, EventsView, type OrgCircle } from './ManageEvents';
import './CircleManage.css';

type CircleDetail = {
  id: string;
  name: string;
  city?: string;
  description?: string;
  organizerId?: string;
  organizerName?: string;
  status?: string;
  createdAt?: string;
  memberCount?: number;
  coverImageUrl?: string;
  category?: string;
  tags?: string[];
  hidden?: boolean;
};

type Overview = {
  circle?: CircleDetail;
  counts?: { members?: number; totalEvents?: number; upcomingEvents?: number; pendingRequests?: number };
};

type Member = {
  membershipId?: string;
  readerId?: string;
  readerName?: string;
  readerEmail?: string;
  readerRole?: string;
  status?: string;
  requestedAt?: string;
  decidedAt?: string;
  avatarUrl?: string;
};

type OrgRow = {
  userId?: string;
  name?: string;
  avatarUrl?: string;
  city?: string;
  joinedAt?: string;
  addedAt?: string;
};

type OrgsResp = {
  current?: OrgRow[];
  past?: OrgRow[];
  currentCount?: number;
  pastCount?: number;
};

type UserOpt = { id: string; name?: string; role?: string };

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/* ------------------------------------------------------------------ */
/* Edit Circle modal                                                   */
/* ------------------------------------------------------------------ */

function EditCircleModal({
  circle,
  onClose,
  onSaved,
}: {
  circle: CircleDetail;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(circle.name || '');
  const [city, setCity] = useState(circle.city || '');
  const [desc, setDesc] = useState(circle.description || '');
  const [category, setCategory] = useState(circle.category || '');
  const [tags, setTags] = useState((circle.tags || []).join(', '));
  const [cover] = useState(circle.coverImageUrl || '');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState('');

  async function save() {
    if (!name.trim()) { setErr('Circle name is required.'); return; }
    setSaving(true);
    setErr('');
    try {
      let coverUrl = cover;
      if (file) {
        setUploading(true);
        const fd = new FormData();
        fd.append('file', file);
        const t = localStorage.getItem('rc_access');
        const r = await fetch(
          apiBase() + '/api/circles/' + circle.id + '/cover',
          { method: 'POST', headers: t ? { Authorization: 'Bearer ' + t } : {}, body: fd },
        );
        const d = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error((d as { message?: string }).message || 'Cover upload failed.');
        coverUrl = (d as { coverImageUrl?: string }).coverImageUrl || coverUrl;
        setUploading(false);
      }
      await api('/api/circles/' + circle.id, {
        method: 'PATCH',
        body: JSON.stringify({
          name: name.trim(),
          city: city.trim() || null,
          description: desc.trim() || null,
          category: category.trim() || null,
          tags: tags.split(',').map((x) => x.trim()).filter(Boolean),
          coverImageUrl: coverUrl || null,
        }),
      });
      onSaved();
    } catch (e) {
      setErr(errMsg(e));
    } finally {
      setSaving(false);
      setUploading(false);
    }
  }

  return (
    <div className="modalback" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <button className="x" onClick={onClose} aria-label="Close">×</button>
        <h2>Edit Circle</h2>
        <p className="msub">Update name, description, tags and cover image.</p>
        <label>Circle Name</label>
        <input type="text" value={name} maxLength={50} onChange={(e) => setName(e.target.value)} />
        <div className="count">{name.length}/50</div>
        <div className="row2">
          <div>
            <label>City</label>
            <input type="text" value={city} onChange={(e) => setCity(e.target.value)} />
          </div>
          <div>
            <label>Category</label>
            <input type="text" value={category} onChange={(e) => setCategory(e.target.value)} />
          </div>
        </div>
        <label>Description</label>
        <textarea value={desc} maxLength={300} onChange={(e) => setDesc(e.target.value)} />
        <div className="count">{desc.length}/300</div>
        <label>Tags (comma separated)</label>
        <input type="text" placeholder="Tamil Literature, Classics, Poetry" value={tags} onChange={(e) => setTags(e.target.value)} />
        <label>Cover Image</label>
        <div className="upbox" onClick={() => document.getElementById('mg-circle-cover')?.click()}>
          {(file || cover) && (
            <img src={file ? URL.createObjectURL(file) : resolveMedia(cover)} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
          )}
          <div>{uploading ? 'Uploading…' : 'Click to choose a cover image'}</div>
          <div style={{ fontSize: 12 }}>JPG, PNG, WebP (Max 5MB)</div>
        </div>
        <input id="mg-circle-cover" type="file" accept="image/jpeg,image/png,image/webp" style={{ display: 'none' }} onChange={(e) => setFile(e.target.files?.[0] || null)} />
        {err && <div className="merr">{err}</div>}
        <div className="foot">
          <button className="btn ghost" onClick={onClose} disabled={saving}>Cancel</button>
          <button className="btn" onClick={() => void save()} disabled={saving || uploading}>
            {saving ? 'Saving…' : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Members tab (mockup screen 9)                                       */
/* ------------------------------------------------------------------ */

function MembersTab({ circleId }: { circleId: string }) {
  const [list, setList] = useState<Member[]>([]);
  const [q, setQ] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState('');
  const [meId, setMeId] = useState('');

  async function load() {
    try {
      const m = await api<Me>('/api/users/me');
      setMeId(m.id || '');
      const data = await api<Member[]>('/api/circles/' + circleId + '/members');
      setList(data || []);
      setErr('');
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [circleId]);

  const shown = list.filter((m) => {
    const t = q.trim().toLowerCase();
    if (!t) return true;
    return (m.readerName || '').toLowerCase().includes(t) || (m.readerEmail || '').toLowerCase().includes(t);
  });

  async function remove(userId?: string, name?: string) {
    if (!userId) return;
    if (!window.confirm(`Remove ${name || 'this member'} from the circle?`)) return;
    setBusy(userId);
    try {
      await api('/api/circles/' + circleId + '/members/' + userId, { method: 'DELETE' });
      await load();
    } catch (e) {
      alert(errMsg(e));
    } finally {
      setBusy('');
    }
  }

  return (
    <div>
      <div className="secttl">
        <div>
          <h2>👥 Members ({list.length})</h2>
          <p>Members of this circle.</p>
        </div>
        <div className="sp" />
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="🔍 Search members…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>
      {err && <div className="mg-err">{err}</div>}
      <div className="card" style={{ padding: 6 }}>
        <table className="tbl">
          <thead><tr><th>#</th><th>Name</th><th>Joined On</th><th>Action</th></tr></thead>
          <tbody>
            {shown.map((m, i) => {
              const self = m.readerId === meId;
              return (
                <tr key={m.membershipId || m.readerId || i}>
                  <td>{i + 1}</td>
                  <td>
                    <span className="who">
                      <span className="face">{initialsOf(m.readerName)}</span>
                      {m.readerName}{self ? ' (You)' : ''}
                      {self ? <span className="pill org">👑 Organizer</span> : null}
                    </span>
                  </td>
                  <td>{fmtDate(m.decidedAt || m.requestedAt)}</td>
                  <td>
                    {!self ? (
                      <button className="btn danger-ghost rowbtn" disabled={busy === m.readerId} onClick={() => void remove(m.readerId, m.readerName)}>
                        👤 Remove
                      </button>
                    ) : (
                      <span style={{ color: '#a39a8a' }}>—</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {!shown.length && !err && <div className="emptybox">No members found.</div>}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Join Requests tab (mockup screen 10)                                */
/* ------------------------------------------------------------------ */

function RequestsTab({ circleId, onChanged }: { circleId: string; onChanged: () => void }) {
  const [list, setList] = useState<Member[]>([]);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState('');

  async function load() {
    try {
      const data = await api<Member[]>('/api/circles/' + circleId + '/join-requests');
      setList(data || []);
      setErr('');
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [circleId]);

  async function decide(id?: string, action?: 'APPROVE' | 'REJECT') {
    if (!id) return;
    setBusy(id + action);
    try {
      await api('/api/join-requests/' + id, { method: 'PATCH', body: JSON.stringify({ action }) });
      await load();
      onChanged();
    } catch (e) {
      alert(errMsg(e));
    } finally {
      setBusy('');
    }
  }

  return (
    <div>
      <div className="secttl">
        <div>
          <h2>🧑‍🤝‍🧑 Join Requests ({list.length})</h2>
          <p>Readers who have requested to join this circle.</p>
        </div>
      </div>
      {err && <div className="mg-err">{err}</div>}
      <div className="card" style={{ padding: 6 }}>
        <table className="tbl">
          <thead><tr><th>#</th><th>Name</th><th>Requested On</th><th>Action</th></tr></thead>
          <tbody>
            {list.map((m, i) => (
              <tr key={m.membershipId || i}>
                <td>{i + 1}</td>
                <td>
                  <span className="who">
                    <span className="face">{initialsOf(m.readerName)}</span>
                    {m.readerName}
                  </span>
                </td>
                <td>{fmtDate(m.requestedAt)}</td>
                <td>
                  <span className="act">
                    <button className="btn ok-ghost rowbtn" disabled={!!busy} onClick={() => void decide(m.membershipId, 'APPROVE')}>
                      ✓ Accept
                    </button>
                    <button className="btn danger-ghost rowbtn" disabled={!!busy} onClick={() => void decide(m.membershipId, 'REJECT')}>
                      ✕ Reject
                    </button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!list.length && !err && <div className="emptybox">No pending join requests.</div>}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* ------------------------------------------------------------------ */
/* Manage area shell (mockup screens 3/4/5/7/8/9/10)                    */
/* ------------------------------------------------------------------ */

type Tab = 'overview' | 'events' | 'members' | 'requests';

export function ManageArea({ circleId, backTo, onBack }: { circleId?: string; backTo?: string; onBack?: () => void } = {}) {
  const { id: routeId } = useParams();
  const id = circleId ?? routeId;
  const backTarget = backTo ?? '/reader-circles';

  /* Back control: embedded hosts (admin) pass onBack to reset their own
     state — a <Link> to the same route would not remount or clear it. */
  function renderBack(fontSize?: number) {
    const style = { color: '#2f5d50', fontWeight: 700, fontSize } as const;
    if (onBack) {
      return (
        <button
          type="button"
          onClick={onBack}
          style={{ ...style, background: 'none', border: 0, padding: 0, cursor: 'pointer' }}
        >
          ← Back to Circles
        </button>
      );
    }
    return (
      <Link to={backTarget} style={style}>
        ← Back to Circles
      </Link>
    );
  }
  const [circle, setCircle] = useState<CircleDetail | null>(null);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [allowed, setAllowed] = useState<boolean | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [tab, setTab] = useState<Tab>('overview');
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);
  const [showEdit, setShowEdit] = useState(false);
  const [evSub, setEvSub] = useState<null | { view: 'detail' | 'attendees' | 'chat'; id: string; title: string }>(null);
  const [recent, setRecent] = useState<Member[]>([]);
  /* Admin-only organiser management */
  const [orgs, setOrgs] = useState<OrgsResp | null>(null);
  const [orgCandidates, setOrgCandidates] = useState<UserOpt[]>([]);
  const [orgBusy, setOrgBusy] = useState(false);
  const [orgErr, setOrgErr] = useState('');
  const [changingPrimary, setChangingPrimary] = useState(false);
  const [newPrimaryId, setNewPrimaryId] = useState('');

  async function load() {
    if (!id) return;
    try {
      const [c, o, orgs, me, og] = await Promise.all([
        api<CircleDetail>('/api/circles/' + id),
        api<Overview>('/api/circles/' + id + '/overview').catch(() => null),
        api<OrgCircle[]>('/api/users/me/organized-circles').catch(() => [] as OrgCircle[]),
        api<Me>('/api/users/me').catch(() => null),
        api<OrgsResp>('/api/circles/' + id + '/organizers').catch(() => null),
      ]);
      setCircle(c);
      setOverview(o);
      setOrgs(og);
      const admin = (me?.role || '') === 'ADMIN';
      setIsAdmin(admin);
      setAllowed(admin || (orgs || []).some((x) => String(x.id) === String(id)));
      setErr('');
      try {
        const m = await api<Member[]>('/api/circles/' + id + '/members');
        setRecent((m || []).slice(0, 4));
      } catch { /* optional */ }
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [id, tick]);

  useEffect(() => {
    if (!isAdmin || !id) return;
    (async () => {
      try {
        const list = await api<UserOpt[]>('/api/users/organizers');
        setOrgCandidates(Array.isArray(list) ? list : []);
      } catch { /* appoint picker stays empty */ }
    })();
  }, [isAdmin, id]);

  async function reassignPrimary() {
    if (!id || !newPrimaryId) return;
    setOrgBusy(true);
    setOrgErr('');
    try {
      await api('/api/circles/' + id, {
        method: 'PATCH',
        body: JSON.stringify({ organizerId: newPrimaryId }),
      });
      setChangingPrimary(false);
      setNewPrimaryId('');
      setTick((t) => t + 1);
    } catch (e) {
      setOrgErr(errMsg(e));
    } finally {
      setOrgBusy(false);
    }
  }

  if (err) {
    return (
      <div className="mg-wrap">
        {renderBack()}
        <div className="mg-err" style={{ marginTop: 12 }}>{err}</div>
      </div>
    );
  }
  if (!circle || allowed === null) {
    return (
      <div className="mg-wrap">
        <div className="emptybox">Loading circle…</div>
      </div>
    );
  }
  if (!allowed) {
    return (
      <div className="mg-wrap">
        {renderBack()}
        <div className="mg-err" style={{ marginTop: 12 }}>Only the appointed organizer of this circle can open the manage screen.</div>
      </div>
    );
  }

  const counts = overview?.counts || {};
  const pending = counts.pendingRequests ?? 0;
  const curOrgs = orgs?.current || [];
  const primaryOrg =
    curOrgs.find((o) => !!circle?.organizerId && String(o.userId) === String(circle?.organizerId)) || curOrgs[0];

  if (evSub) {
    const back = () => setEvSub(null);
    return (
      <div className="mg-wrap">
        {renderBack(13.5)}
        {evSub.view === 'detail' && (
          <EventDetailWrap
            eventId={evSub.id}
            circleId={circle.id}
            asMember={false}
            onBack={back}
            onAttendees={(t) => setEvSub({ view: 'attendees', id: evSub.id, title: t })}
            onChat={(t) => setEvSub({ view: 'chat', id: evSub.id, title: t })}
          />
        )}
        {evSub.view === 'attendees' && <AttendeesWrap eventId={evSub.id} title={evSub.title} onBack={() => setEvSub({ view: 'detail', id: evSub.id, title: evSub.title })} />}
        {evSub.view === 'chat' && <ChatWrap eventId={evSub.id} title={evSub.title} onBack={() => setEvSub({ view: 'detail', id: evSub.id, title: evSub.title })} />}
      </div>
    );
  }

  const tabs: { k: Tab; label: string; badge?: number }[] = [
    { k: 'overview', label: '🏠 Overview' },
    { k: 'events', label: '📅 Events' },
    { k: 'members', label: '👥 Members' },
    { k: 'requests', label: '🧑‍🤝‍🧑 Join Requests', badge: pending || undefined },
  ];

  return (
    <div className="mg-wrap">
        {renderBack(13.5)}
        <div className="mg-hero" style={{ marginTop: 8 }}>
          <img src={resolveMedia(circle.coverImageUrl) || COVER_PLACEHOLDER} alt={'Cover banner of ' + (circle.name || 'circle')} onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = COVER_PLACEHOLDER; }} />
        </div>
        <div className="mg-head">
          {circle.coverImageUrl ? <img className="mg-avatar" src={resolveMedia(circle.coverImageUrl)} alt={'Avatar of ' + (circle.name || 'circle')} onError={(e) => { e.currentTarget.style.display = 'none'; }} /> : <span className="mg-avatar" />}
          <div className="mg-title">
            <h1>{circle.name} <span className="pill org">{isAdmin ? '👑 Admin' : '👑 Organizer'}</span></h1>
            <p className="desc">{circle.description}</p>
            <div className="mg-meta">
              <span>👥 {circle.memberCount ?? counts.members ?? '–'} member{(circle.memberCount ?? 0) === 1 ? '' : 's'}</span>
              <span>📅 {counts.upcomingEvents ?? '–'} upcoming events</span>
              {circle.category ? <span className="tag">{circle.category}</span> : null}
              {isAdmin && circle.organizerName ? <span>👑 Organiser: {circle.organizerName}</span> : null}
            </div>
          </div>
        </div>

        <div className="mg-tabs">
          {tabs.map((t) => (
            <button key={t.k} className={tab === t.k ? 'on' : ''} onClick={() => setTab(t.k)}>
              {t.label}{t.badge ? <span className="cbadge">{t.badge}</span> : null}
            </button>
          ))}
        </div>

        {tab === 'overview' && (
          <div className="mg-cols">
            <div>
              <div className="card">
                <div className="headrow">
                  <h3>🧺 Circle Information</h3>
                  <button className="btn ghost rowbtn" onClick={() => setShowEdit(true)}>✎ Edit Circle</button>
                </div>
                <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
                  <div className="coverbox">
                    <img src={resolveMedia(circle.coverImageUrl) || COVER_PLACEHOLDER} alt={'Cover image of ' + (circle.name || 'circle')} onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = COVER_PLACEHOLDER; }} />
                    <div className="cap">Cover Image</div>
                  </div>
                  <div style={{ flex: '1 1 220px', minWidth: 0 }}>
                    <div className="kv"><b>Circle Name</b><span>{circle.name}</span></div>
                    <div className="kv"><b>Description</b><span>{circle.description || '—'}</span></div>
                    <div className="kv"><b>Tags</b>
                      {(circle.tags || []).length ? (
                        <span className="tagrow">{(circle.tags || []).map((t, i) => <span key={i}>{t}</span>)}</span>
                      ) : (
                        <span>—</span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
              {isAdmin && (
                <div className="card mg-orgs">
                  <div className="headrow">
                    <h3>👑 Circle Organiser</h3>
                    <button
                      className="btn rowbtn"
                      disabled={orgBusy}
                      onClick={() => {
                        setChangingPrimary((v) => !v);
                        setNewPrimaryId('');
                        setOrgErr('');
                      }}
                    >
                      {changingPrimary ? 'Cancel' : 'Change Organiser'}
                    </button>
                  </div>
                  <div className="mg-org-row">
                    <span className="face">{initialsOf(primaryOrg?.name)}</span>
                    <div className="mg-org-who">
                      <b>{primaryOrg?.name || 'No organiser assigned'}</b>
                      <small>
                        Organiser
                        {primaryOrg && (primaryOrg.addedAt || primaryOrg.joinedAt)
                          ? ' · Since ' + fmtDate(primaryOrg.addedAt || primaryOrg.joinedAt)
                          : ''}
                      </small>
                    </div>
                  </div>
                  {orgErr && <div className="mg-err" style={{ marginTop: 10 }}>{orgErr}</div>}
                  {changingPrimary && (
                    <div className="mg-org-edit">
                      <select value={newPrimaryId} onChange={(e) => setNewPrimaryId(e.target.value)} aria-label="Select new organiser">
                        <option value="">Select new organiser…</option>
                        {orgCandidates
                          .filter((c) => String(c.id) !== String(circle.organizerId))
                          .map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name} · {c.role}
                            </option>
                          ))}
                      </select>
                      <button className="btn" disabled={orgBusy || !newPrimaryId} onClick={() => void reassignPrimary()}>
                        Confirm
                      </button>
                    </div>
                  )}
                </div>
              )}
              <div className="card">
                <div className="statrow">
                  <div className="stat"><span className="ic">👥</span><b>{counts.members ?? circle.memberCount ?? '–'}</b><span>Total Members</span></div>
                  <div className="stat"><span className="ic">📅</span><b>{counts.upcomingEvents ?? '–'}</b><span>Upcoming Events</span></div>
                  <div className="stat"><span className="ic">💬</span><DiscCount circleId={circle.id} /><span>Discussions</span></div>
                  <div className="stat"><span className="ic">📚</span><b>–</b><span>Books Recommended</span></div>
                </div>
              </div>
            </div>
            <div>
              <div className="card">
                <div className="headrow">
                  <h3>👥 Recent Members</h3>
                  <a onClick={() => setTab('members')}>View All →</a>
                </div>
                {recent.map((m) => (
                  <div className="memrow" key={m.membershipId || m.readerId}>
                    <span className="face">{initialsOf(m.readerName)}</span>
                    <div>
                      <b>{m.readerName}</b>
                      <div><small>Joined {fmtDate(m.decidedAt || m.requestedAt)}</small></div>
                    </div>
                  </div>
                ))}
                {!recent.length && <p className="sub">No members yet.</p>}
              </div>
            </div>
          </div>
        )}

        {tab === 'events' && (
          <EventsView
            circleId={circle.id}
            circleName={circle.name}
            onOpenEvent={(eid, sub) => setEvSub({ view: sub, id: eid, title: '' })}
          />
        )}
        {tab === 'members' && <MembersTab circleId={circle.id} />}
        {tab === 'requests' && <RequestsTab circleId={circle.id} onChanged={() => setTick((t) => t + 1)} />}

        {showEdit && (
          <EditCircleModal circle={circle} onClose={() => setShowEdit(false)} onSaved={() => { setShowEdit(false); setTick((t) => t + 1); }} />
        )}
    </div>
  );
}

function DiscCount({ circleId }: { circleId: string }) {
  const [n, setN] = useState<number | null>(null);
  useEffect(() => {
    let on = true;
    (async () => {
      try {
        const data = await api<{ circleId?: string }[]>('/api/discussions');
        if (!on) return;
        setN((data || []).filter((d) => String(d.circleId || '') === String(circleId)).length);
      } catch {
        if (on) setN(null);
      }
    })();
    return () => { on = false; };
  }, [circleId]);
  return <b>{n === null ? '–' : n}</b>;
}

/* Small wrappers so the event screens keep their own back targets. */

function EventDetailWrap({ eventId, circleId, asMember, onBack, onAttendees, onChat }: {
  eventId: string; circleId: string; asMember: boolean; onBack: () => void;
  onAttendees: (title: string) => void; onChat: (title: string) => void;
}) {
  return <EventDetailView eventId={eventId} circleId={circleId} asMember={asMember} onBack={onBack} onAttendees={() => onAttendees('')} onChat={() => onChat('')} />;
}

function AttendeesWrap({ eventId, title, onBack }: { eventId: string; title: string; onBack: () => void }) {
  const [name, setName] = useState(title);
  useEffect(() => {
    if (name) return;
    (async () => {
      try {
        const d = await api<{ title?: string }>('/api/events/' + eventId);
        setName(d.title || '');
      } catch { /* ignore */ }
    })();
    /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, [eventId]);
  return <AttendeesView eventId={eventId} title={title || name} onBack={onBack} />;
}

function ChatWrap({ eventId, title, onBack }: { eventId: string; title: string; onBack: () => void }) {
  const [name, setName] = useState(title);
  useEffect(() => {
    if (name) return;
    (async () => {
      try {
        const d = await api<{ title?: string }>('/api/events/' + eventId);
        setName(d.title || '');
      } catch { /* ignore */ }
    })();
    /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, [eventId]);
  return <EventChatView eventId={eventId} title={title || name} onBack={onBack} />;
}
