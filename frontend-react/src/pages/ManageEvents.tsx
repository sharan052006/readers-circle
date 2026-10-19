import { useEffect, useState } from 'react';
import { api, fmtDate, fmtTime, initialsOf, type Me } from '../lib/api';
import { TN_DISTRICTS } from '../lib/tnDistricts';
import { COVER_PLACEHOLDER } from '../lib/placeholders';
import './CircleManage.css';

export type OrgCircle = { id: string; name: string; coverImageUrl?: string };

export type MEvent = {
  id: string;
  circleId?: string;
  circleName?: string;
  title?: string;
  description?: string;
  readingTopic?: string;
  guidelines?: string;
  eventDate?: string;
  eventTime?: string;
  venue?: string;
  location?: string;
  capacity?: number;
  registeredCount?: number;
  registrationDeadline?: string;
  coverImageUrl?: string;
  status?: string;
  isRegistered?: boolean;
  isOrganizerOrAdmin?: boolean;
  organizerId?: string;
  createdAt?: string;
  type?: string;
  mode?: string;
};

export type Attendee = {
  readerId?: string;
  fullName?: string;
  email?: string;
  registeredAt?: string;
  avatarUrl?: string;
  attended?: boolean;
};

type ChatMsg = {
  id?: string;
  authorId?: string;
  author?: string;
  avatarUrl?: string;
  content?: string;
  createdAt?: string;
};

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function dayOf(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return String(iso).slice(0, 10);
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function dateTimeOf(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return String(iso).slice(0, 16).replace('T', ' ');
  return (
    d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) +
    ', ' +
    d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
  );
}

function statusPill(s?: string): string {
  const v = String(s || '').toUpperCase();
  if (v === 'DRAFT') return 'pill draft';
  if (v === 'PUBLISHED' || v === 'UPCOMING') return 'pill upcoming';
  return 'pill past';
}

/* ------------------------------------------------------------------ */
/* Create / Edit Event modal (mockup screens 2 & 6)                    */
/* Single shared form for organiser + admin: title, description,       */
/* topic, date/time, venue, capacity, cover image (file), draft/live.  */
/* ------------------------------------------------------------------ */

export function EventForm({
  circles,
  fixedCircleId,
  initial,
  onClose,
  onSaved,
}: {
  circles: OrgCircle[];
  fixedCircleId?: string;
  initial?: MEvent | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isEdit = !!initial?.id;
  const [circleId, setCircleId] = useState(fixedCircleId || initial?.circleId || circles[0]?.id || '');
  const [title, setTitle] = useState(initial?.title || '');
  const [desc, setDesc] = useState(initial?.description || '');
  const [topic, setTopic] = useState(initial?.readingTopic || '');
  const [date, setDate] = useState((initial?.eventDate || '').slice(0, 10));
  const [time, setTime] = useState((initial?.eventTime || '').slice(0, 5));
  const [venue, setVenue] = useState(initial?.venue || '');
  const [location, setLocation] = useState(initial?.location || '');
  const [capacity, setCapacity] = useState(String(initial?.capacity ?? 30));
  const [cover, setCover] = useState(initial?.coverImageUrl || '');
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState('');
  const [editing, setEditing] = useState<'basic' | 'schedule' | 'cover' | null>(null);

  function onFile(f: File | undefined) {
    if (!f) return;
    const ext = (f.name.split('.').pop() || '').toLowerCase();
    if (!['jpg', 'jpeg', 'png', 'webp'].includes(ext)) {
      setErr('Only JPG, PNG or WEBP images are allowed.');
      return;
    }
    if (f.size > 5 * 1024 * 1024) {
      setErr('Cover image must be under 5MB.');
      return;
    }
    setErr('');
    const r = new FileReader();
    r.onload = () => {
      if (typeof r.result === 'string') setCover(r.result);
    };
    r.readAsDataURL(f);
  }

  function payload(publish: boolean) {
    const cap = parseInt(capacity, 10);
    return {
      title: title.trim(),
      description: desc.trim() || null,
      readingTopic: topic.trim() || null,
      eventDate: date || null,
      eventTime: time ? (time.length === 5 ? time + ':00' : time) : null,
      venue: venue.trim() || null,
      location: location || null,
      capacity: Number.isFinite(cap) ? cap : null,
      coverImageUrl: cover || null,
      publishNow: publish,
    };
  }

  function valid(): string {
    if (!circleId) return 'Pick a circle.';
    if (!title.trim()) return 'Event title is required.';
    if (!date) return 'Date is required.';
    if (!time) return 'Start time is required.';
    if (!isEdit && !location) return 'Select a location (district).';
    if (!venue.trim()) return 'Venue is required.';
    const cap = parseInt(capacity, 10);
    if (!Number.isFinite(cap) || cap <= 0) return 'Capacity must be greater than 0.';
    return '';
  }

  async function save(publish: boolean): Promise<boolean> {
    const v = valid();
    if (v) {
      setErr(v);
      return false;
    }
    setSaving(true);
    setErr('');
    try {
      if (isEdit) {
        await api('/api/events/' + initial!.id, { method: 'PATCH', body: JSON.stringify(payload(publish)) });
      } else {
        await api('/api/circles/' + circleId + '/events', {
          method: 'POST',
          body: JSON.stringify(payload(publish)),
        });
      }
      return true;
    } catch (e) {
      setErr(errMsg(e));
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function create(publish: boolean) {
    if (await save(publish)) onSaved();
  }

  async function saveSection() {
    if (await save(true)) {
      setEditing(null);
      onSaved();
    }
  }

  return (
    <>
      <div className="efo-head">
        <span className="efo-head-ico">📅</span>
        <div className="efo-head-txt">
          <h2>{isEdit ? 'Edit Event' : 'Create New Event'}</h2>
          <p>{isEdit ? 'Edit each section of your event on its own.' : 'Organise a reading event for your circle.'}</p>
        </div>
        <div className="efo-head-actions">
          {isEdit ? (
            <button className="btn ghost" onClick={onClose} disabled={saving}>Close</button>
          ) : (
            <>
              <button className="btn ghost" onClick={onClose} disabled={saving}>Cancel</button>
              <button className="btn ghost" onClick={() => void create(false)} disabled={saving}>
                {saving ? 'Saving…' : 'Save as draft'}
              </button>
              <button className="btn" onClick={() => void create(true)} disabled={saving}>
                {saving ? 'Saving…' : 'Create Event'}
              </button>
            </>
          )}
        </div>
      </div>

      {err && <div className="merr">{err}</div>}

      <div className="efo-grid">
        <div className="efo-col">
          <section className="efo-sec">
            <span className="efo-sec-ico">📝</span>
            <div className="efo-sec-body">
              <div className="efo-sec-head efo-sec-head-row">
                <div>
                  <h3>Basic Information</h3>
                  <p>Title, description and reading topic.</p>
                </div>
                {isEdit ? (
                  editing === 'basic' ? (
                    <div className="efo-sec-actions">
                      <button className="btn ghost" onClick={() => setEditing(null)} disabled={saving}>Cancel</button>
                      <button className="btn" onClick={() => void saveSection()} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                  ) : (
                    <button className="efo-editbtn" onClick={() => { setErr(''); setEditing('basic'); }}>✎ Edit</button>
                  )
                ) : null}
              </div>
              {!isEdit || editing === 'basic' ? (
                <>
                  {!fixedCircleId && !isEdit && (
                    <div className="efo-fld">
                      <label>Circle <span className="req">*</span></label>
                      <select value={circleId} onChange={(e) => setCircleId(e.target.value)}>
                        {circles.map((c) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                    </div>
                  )}
                  <div className="efo-fld">
                    <label>Event Title <span className="req">*</span><span className="efo-count">{title.length}/100</span></label>
                    <input type="text" placeholder="Enter a clear and short event title" value={title} maxLength={100} onChange={(e) => setTitle(e.target.value)} />
                  </div>
                  <div className="efo-fld">
                    <label>Event Description <span className="req">*</span><span className="efo-count">{desc.length}/500</span></label>
                    <textarea placeholder="Describe what this event is about. You can include the book name, discussion points, or any other details." value={desc} maxLength={500} onChange={(e) => setDesc(e.target.value)} />
                  </div>
                  <div className="efo-fld">
                    <label>Reading Topic / Book <span className="req">*</span></label>
                    <input type="text" placeholder="Book name or discussion topic (e.g. Ponniyin Selvan, Modern Tamil Poetry, etc.)" value={topic} onChange={(e) => setTopic(e.target.value)} />
                  </div>
                </>
              ) : (
                <div className="efo-view">
                  <div><span>Event Title</span><b>{title || '—'}</b></div>
                  <div><span>Event Description</span><b>{desc || '—'}</b></div>
                  <div><span>Reading Topic / Book</span><b>{topic || '—'}</b></div>
                </div>
              )}
            </div>
          </section>
        </div>

        <div className="efo-col">
          <section className="efo-sec">
            <span className="efo-sec-ico">🗓️</span>
            <div className="efo-sec-body">
              <div className="efo-sec-head efo-sec-head-row">
                <div>
                  <h3>Schedule &amp; Location</h3>
                  <p>Date, time and where the event will take place.</p>
                </div>
                {isEdit ? (
                  editing === 'schedule' ? (
                    <div className="efo-sec-actions">
                      <button className="btn ghost" onClick={() => setEditing(null)} disabled={saving}>Cancel</button>
                      <button className="btn" onClick={() => void saveSection()} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                  ) : (
                    <button className="efo-editbtn" onClick={() => { setErr(''); setEditing('schedule'); }}>✎ Edit</button>
                  )
                ) : null}
              </div>
              {!isEdit || editing === 'schedule' ? (
                <>
                  <div className="efo-row3">
                    <div className="efo-fld">
                      <label>Date <span className="req">*</span></label>
                      <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
                    </div>
                    <div className="efo-fld">
                      <label>Start Time <span className="req">*</span></label>
                      <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
                    </div>
                    <div className="efo-fld">
                      <label>Capacity <span className="req">*</span></label>
                      <input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
                    </div>
                  </div>
                  <div className="efo-row2">
                    <div className="efo-fld">
                      <label>Location <span className="req">*</span></label>
                      <select value={location} onChange={(e) => setLocation(e.target.value)}>
                        <option value="">Select a district</option>
                        {TN_DISTRICTS.map((d) => (
                          <option key={d} value={d}>{d}</option>
                        ))}
                      </select>
                      <div className="efo-hint">Pick the Tamil Nadu district where this event takes place.</div>
                    </div>
                    <div className="efo-fld">
                      <label>Venue / Meeting Link <span className="req">*</span></label>
                      <input type="text" placeholder="Venue or meeting link" value={venue} onChange={(e) => setVenue(e.target.value)} />
                      <div className="efo-hint">This can be a physical location or an online meeting link.</div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="efo-view">
                  <div><span>Date</span><b>{date ? fmtDate(date) : '—'}</b></div>
                  <div><span>Start Time</span><b>{time || '—'}</b></div>
                  <div><span>Capacity</span><b>{capacity || '—'}</b></div>
                  <div><span>Location</span><b>{location || '—'}</b></div>
                  <div><span>Venue / Meeting Link</span><b>{venue || '—'}</b></div>
                </div>
              )}
            </div>
          </section>
        </div>

        <div className="efo-col">
          <section className="efo-sec">
            <span className="efo-sec-ico">🖼️</span>
            <div className="efo-sec-body">
              <div className="efo-sec-head efo-sec-head-row">
                <div>
                  <h3>Cover Image</h3>
                  <p>The event cover image.</p>
                </div>
                {isEdit ? (
                  editing === 'cover' ? (
                    <div className="efo-sec-actions">
                      <button className="btn ghost" onClick={() => setEditing(null)} disabled={saving}>Cancel</button>
                      <button className="btn" onClick={() => void saveSection()} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                  ) : (
                    <button className="efo-editbtn" onClick={() => { setErr(''); setEditing('cover'); }}>✎ Edit</button>
                  )
                ) : null}
              </div>
              {!isEdit || editing === 'cover' ? (
                <>
                  <div className="efo-cover">
                    {cover ? (
                      <div className="efo-cover-prev">
                        <img src={cover} alt="" onError={(e) => { e.currentTarget.style.display = 'none'; }} />
                      </div>
                    ) : null}
                    <div className="efo-drop" onClick={() => document.getElementById('mg-ev-cover')?.click()}>
                      <span className="efo-drop-ic">⬆️</span>
                      <div>{cover ? 'Click to upload a new cover image' : 'Click to upload a cover image'}</div>
                      <small>JPG, PNG, WebP (Max 5MB)</small>
                    </div>
                  </div>
                  <input id="mg-ev-cover" type="file" accept="image/jpeg,image/png,image/webp" style={{ display: 'none' }} onChange={(e) => onFile(e.target.files?.[0])} />
                  {cover && (
                    <button className="btn ghost efo-remove" onClick={() => setCover('')}>Remove Image</button>
                  )}
                </>
              ) : (
                cover ? <img className="efo-cover-view" src={cover} alt="" /> : <div className="efo-cover-empty">No cover image</div>
              )}
            </div>
          </section>
        </div>
      </div>
    </>
  );
}

export function EventModal({
  circles,
  fixedCircleId,
  initial,
  onClose,
  onSaved,
}: {
  circles: OrgCircle[];
  fixedCircleId?: string;
  initial?: MEvent | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  return (
    <div className="modalback" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <EventForm
          circles={circles}
          fixedCircleId={fixedCircleId}
          initial={initial}
          onClose={onClose}
          onSaved={onSaved}
        />
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Events tab (mockup screen 4)                                        */
/* ------------------------------------------------------------------ */

export function EventsView({
  circleId,
  circleName,
  onOpenEvent,
}: {
  circleId: string;
  circleName: string;
  onOpenEvent: (id: string, sub: 'detail' | 'attendees' | 'chat') => void;
}) {
  const [scope, setScope] = useState<'upcoming' | 'past' | 'drafts'>('upcoming');
  const [list, setList] = useState<MEvent[]>([]);
  const [q, setQ] = useState('');
  const [sort, setSort] = useState('recent');
  const [showModal, setShowModal] = useState(false);
  const [editEv, setEditEv] = useState<MEvent | null>(null);
  const [err, setErr] = useState('');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let on = true;
    (async () => {
      try {
        const params = scope === 'drafts' ? '?scope=all&status=DRAFT' : '?scope=' + scope;
        const data = await api<MEvent[]>('/api/circles/' + circleId + '/events' + params);
        if (!on) return;
        setList(data || []);
        setErr('');
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => { on = false; };
  }, [circleId, scope, tick]);

  const shown = (list || [])
    .filter((e) => {
      const t = q.trim().toLowerCase();
      if (!t) return true;
      return (e.title || '').toLowerCase().includes(t) || (e.venue || '').toLowerCase().includes(t) || (e.readingTopic || '').toLowerCase().includes(t);
    })
    .sort((a, b) => {
      if (sort === 'name') return (a.title || '').localeCompare(b.title || '');
      const da = a.eventDate || '';
      const db = b.eventDate || '';
      return sort === 'oldest' ? da.localeCompare(db) : db.localeCompare(da);
    });

  async function cancel(id: string) {
    if (!window.confirm('Cancel this event? Registered members will see it as cancelled.')) return;
    try {
      await api('/api/events/' + id, { method: 'DELETE' });
      setTick((t) => t + 1);
    } catch (e) {
      alert(errMsg(e));
    }
  }

  return (
    <div>
      <div className="secttl">
        <div>
          <h2>📅 Events</h2>
          <p>Create and manage events for {circleName}.</p>
        </div>
        <div className="sp" />
        <button className="btn rowbtn" onClick={() => { setEditEv(null); setShowModal(true); }}>＋ Create Event</button>
      </div>
      <div className="subtabs">
        {(['upcoming', 'past', 'drafts'] as const).map((s) => (
          <button key={s} className={scope === s ? 'on' : ''} onClick={() => setScope(s)}>
            {s === 'upcoming' ? 'Upcoming' : s === 'past' ? 'Past' : 'Drafts'}
          </button>
        ))}
      </div>
      <div className="toolbar">
        <input type="search" placeholder="🔍 Search events…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select value={sort} onChange={(e) => setSort(e.target.value)}>
          <option value="recent">Most Recent</option>
          <option value="oldest">Oldest First</option>
          <option value="name">Name A–Z</option>
        </select>
      </div>
      {err && <div className="mg-err">{err}</div>}
      {!shown.length ? (
        <div className="emptybox">
          <div style={{ fontSize: 30 }}>📅</div>
          <h4>{scope === 'drafts' ? 'No drafts yet' : 'No upcoming events yet'}</h4>
          <p>Create an event and bring your circle together.</p>
          <button className="btn rowbtn" onClick={() => { setEditEv(null); setShowModal(true); }}>＋ Create Event</button>
        </div>
      ) : (
        shown.map((e) => (
          <div className="evcard" key={e.id}>
            <div className="evdate">
              <b>{(e.eventDate || '').slice(8, 10) || '–'}</b>
              <span>{(e.eventDate || '').slice(5, 7) ? new Date((e.eventDate || '') + 'T00:00:00').toLocaleString(undefined, { month: 'short' }).toUpperCase() : ''}</span>
            </div>
            <img className="evthumb" src={e.coverImageUrl || COVER_PLACEHOLDER} alt={'Cover of ' + (e.title || 'event')} onError={(ev2) => { ev2.currentTarget.onerror = null; ev2.currentTarget.src = COVER_PLACEHOLDER; }} />
            <div className="evmain">
              <span className={statusPill(e.status)}>{String(e.status || '').toLowerCase() === 'draft' ? 'Draft' : 'Upcoming'}</span>
              <h4>{e.title}</h4>
              <p>{e.description}</p>
              <div className="evmeta">
                <span>📅 {fmtDate(e.eventDate)} &nbsp; 🕐 {fmtTime(e.eventTime)}</span>
                <span>📍 {[e.location, e.venue].filter(Boolean).join(' · ')}</span>
                <span>👥 {e.registeredCount ?? 0} / {e.capacity ?? '–'} members</span>
              </div>
            </div>
            <div className="evside">
              <button className="btn ghost rowbtn" onClick={() => onOpenEvent(e.id, 'detail')}>👁 View Event</button>
              <button className="btn ghost rowbtn" onClick={() => { setEditEv(e); setShowModal(true); }}>✎ Edit Event</button>
              <button className="btn ghost rowbtn" onClick={() => onOpenEvent(e.id, 'attendees')}>👥 View Attendees</button>
              <button className="btn danger-ghost rowbtn" onClick={() => void cancel(e.id)}>🚫 Cancel Event</button>
            </div>
          </div>
        ))
      )}
      {showModal && (
        <EventModal
          circles={[]}
          fixedCircleId={circleId}
          initial={editEv}
          onClose={() => { setShowModal(false); setEditEv(null); }}
          onSaved={() => { setShowModal(false); setEditEv(null); setTick((t) => t + 1); }}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Event detail (mockup screen 5)                                      */
/* ------------------------------------------------------------------ */

export function EventDetailView({
  eventId,
  circleId,
  asMember,
  onBack,
  onAttendees,
  onChat,
}: {
  eventId: string;
  circleId: string;
  asMember: boolean;
  onBack: () => void;
  onAttendees: () => void;
  onChat: () => void;
}) {
  const [ev, setEv] = useState<MEvent | null>(null);
  const [orgName, setOrgName] = useState('');
  const [preview, setPreview] = useState<Attendee[]>([]);
  const [showEdit, setShowEdit] = useState(false);
  const [err, setErr] = useState('');

  async function load() {
    try {
      const d = await api<MEvent>('/api/events/' + eventId);
      setEv(d);
      setErr('');
      try {
        const o = await api<{ current?: { name?: string }[] }>('/api/circles/' + circleId + '/organizers');
        const cur = (o.current || [])[0];
        if (cur?.name) setOrgName(cur.name);
      } catch { /* name optional */ }
      try {
        const p = await api<Attendee[]>('/api/events/' + eventId + '/registrations');
        setPreview((p || []).slice(0, 5));
      } catch { /* preview optional */ }
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [eventId]);

  async function cancel() {
    if (!window.confirm('Cancel this event?')) return;
    try {
      await api('/api/events/' + eventId, { method: 'DELETE' });
      onBack();
    } catch (e) {
      alert(errMsg(e));
    }
  }

  if (err) return <div className="mg-err">{err}</div>;
  if (!ev) return <div className="emptybox">Loading event…</div>;
  const pct = ev.capacity ? Math.min(100, Math.round(((ev.registeredCount ?? 0) / ev.capacity) * 100)) : 0;

  return (
    <div>
      <button className="mg-back" onClick={onBack}>← Back to Events</button>
      <div className="mg-cols">
        <div>
          <div className="card">
            <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
              <div className="evdate" style={{ height: 'fit-content' }}>
                <b>{(ev.eventDate || '').slice(8, 10)}</b>
                <span>{(ev.eventDate || '').slice(0, 4) ? new Date((ev.eventDate || '') + 'T00:00:00').toLocaleString(undefined, { month: 'short' }).toUpperCase() + ' ' + (ev.eventDate || '').slice(0, 4) : ''}</span>
                <div><span className={statusPill(ev.status)}>{ev.status}</span></div>
              </div>
              <div style={{ flex: '1 1 220px' }}>
                <h2 style={{ fontFamily: 'Georgia, serif', color: '#3c1a12', margin: '0 0 6px', fontSize: 26 }}>{ev.title}</h2>
                <div className="evmeta">
                  {ev.isOrganizerOrAdmin && !asMember ? <span className="pill org">👑 Organized by you</span> : null}
                  <span>📖 {ev.circleName}</span>
                </div>
                <p style={{ color: '#6b6257', fontSize: 13.5 }}>{ev.description}</p>
                <div className="tagrow">
                  {ev.readingTopic ? <span>{ev.readingTopic}</span> : null}
                  {ev.type ? <span>{ev.type}</span> : null}
                  {ev.mode ? <span>{ev.mode}</span> : null}
                </div>
              </div>
              <img src={ev.coverImageUrl || COVER_PLACEHOLDER} alt={'Cover of ' + (ev.title || 'event')} style={{ width: 220, borderRadius: 12, objectFit: 'cover' }} />
            </div>
          </div>
          <div className="card">
            <div className="statrow">
              <div className="stat"><span className="ic">📅</span><b style={{ fontSize: 15 }}>{fmtDate(ev.eventDate)}</b><span>Date</span></div>
              <div className="stat"><span className="ic">🕐</span><b style={{ fontSize: 15 }}>{fmtTime(ev.eventTime)}</b><span>Time</span></div>
              <div className="stat"><span className="ic">📍</span><b style={{ fontSize: 15 }}>{[ev.location, ev.venue].filter(Boolean).join(' · ') || '—'}</b><span>Location</span></div>
              <div className="stat"><span className="ic">👥</span><b style={{ fontSize: 15 }}>{ev.registeredCount ?? 0} / {ev.capacity ?? '–'}</b><span>Capacity</span></div>
            </div>
            <div style={{ background: '#f3ecdb', borderRadius: 999, height: 8, marginTop: 10 }}>
              <div style={{ width: pct + '%', background: '#6b2a1f', height: 8, borderRadius: 999 }} />
            </div>
          </div>
          <div className="card">
            <h3>📖 About This Event</h3>
            <p style={{ color: '#4a443c', fontSize: 14 }}>{ev.description}</p>
            {ev.guidelines ? (
              <>
                <h3 style={{ marginTop: 14 }}>📋 Event Guidelines</h3>
                <p style={{ color: '#4a443c', fontSize: 14 }}>{ev.guidelines}</p>
              </>
            ) : null}
          </div>
        </div>
        <div>
          {!asMember && (
            <div className="card">
              <button className="btn" style={{ width: '100%', marginBottom: 10 }} onClick={() => setShowEdit(true)}>✎ Edit Event</button>
              <button className="btn ghost" style={{ width: '100%', marginBottom: 10 }} onClick={onAttendees}>👥 View Attendees</button>
              <button className="btn ghost" style={{ width: '100%', marginBottom: 10 }} onClick={onChat}>💬 View Event Chat</button>
              <button className="btn danger-ghost" style={{ width: '100%' }} onClick={() => void cancel()}>🚫 Cancel Event</button>
            </div>
          )}
          <div className="card">
            <h3>👥 Organized by</h3>
            <div className="memrow">
              <span className="face">{initialsOf(orgName)}</span>
              <div><b>{orgName || 'Organizer'} {ev.isOrganizerOrAdmin && !asMember ? <span className="pill org">👑 Organizer</span> : null}</b></div>
            </div>
          </div>
          <div className="card">
            <div className="headrow">
              <h3>👥 Attendees ({ev.registeredCount ?? 0} / {ev.capacity ?? '–'})</h3>
              <a onClick={onAttendees}>View All →</a>
            </div>
            {preview.length ? preview.map((a, i) => (
              <div className="memrow" key={i}>
                <span className="face">{a.avatarUrl ? <img src={a.avatarUrl} alt="" /> : initialsOf(a.fullName)}</span>
                <b>{a.fullName}</b>
              </div>
            )) : <p className="sub">No registrations yet.</p>}
          </div>
          <div className="card">
            <h3>📅 Event Details</h3>
            <div className="kv"><b>Circle</b><span>{ev.circleName}</span></div>
            <div className="kv"><b>Location</b><span>{ev.location || '—'}</span></div>
            <div className="kv"><b>Venue</b><span>{ev.venue || '—'}</span></div>
            <div className="kv"><b>Event Type</b><span>{ev.mode === 'ONLINE' ? 'Online' : 'In-Person'}</span></div>
            <div className="kv"><b>Created On</b><span>{fmtDate(ev.createdAt)}</span></div>
          </div>
        </div>
      </div>
      {showEdit && (
        <EventModal
          circles={[]}
          fixedCircleId={circleId}
          initial={ev}
          onClose={() => setShowEdit(false)}
          onSaved={() => { setShowEdit(false); void load(); }}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Attendees (mockup screen 7)                                         */
/* ------------------------------------------------------------------ */

export function AttendeesView({ eventId, title, onBack }: { eventId: string; title: string; onBack: () => void }) {
  const [list, setList] = useState<Attendee[]>([]);
  const [err, setErr] = useState('');

  useEffect(() => {
    let on = true;
    (async () => {
      try {
        const data = await api<Attendee[]>('/api/events/' + eventId + '/registrations');
        if (!on) return;
        setList(data || []);
      } catch (e) {
        if (!on) return;
        setErr(errMsg(e));
      }
    })();
    return () => { on = false; };
  }, [eventId]);

  return (
    <div>
      <button className="mg-back" onClick={onBack}>← Back to Event</button>
      <div className="card">
        <h3>👥 Attendees ({list.length})</h3>
        <p className="sub">{list.length} members have registered for {title || 'this event'}.</p>
        {err && <div className="mg-err">{err}</div>}
        <table className="tbl">
          <thead><tr><th>#</th><th>Name</th><th>Registration Date</th></tr></thead>
          <tbody>
            {list.map((a, i) => (
              <tr key={a.readerId || i}>
                <td>{i + 1}</td>
                <td>
                  <span className="who">
                    <span className="face">{a.avatarUrl ? <img src={a.avatarUrl} alt="" /> : initialsOf(a.fullName)}</span>
                    {a.fullName}
                  </span>
                </td>
                <td>{dateTimeOf(a.registeredAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!list.length && !err && <div className="emptybox">No attendees yet.</div>}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Event chat (mockup screen 8)                                        */
/* ------------------------------------------------------------------ */

export function EventChatView({ eventId, title, onBack }: { eventId: string; title: string; onBack: () => void }) {
  const [members, setMembers] = useState<Attendee[]>([]);
  const [msgs, setMsgs] = useState<ChatMsg[]>([]);
  const [meId, setMeId] = useState('');
  const [text, setText] = useState('');
  const [err, setErr] = useState('');
  const [chatOpen, setChatOpen] = useState(true);

  async function load() {
    try {
      const m = await api<Me>('/api/users/me');
      setMeId(m.id || '');
    } catch { /* ignore */ }
    try {
      const a = await api<Attendee[]>('/api/events/' + eventId + '/registrations');
      setMembers(a || []);
    } catch { /* ignore */ }
    try {
      const c = await api<{ messages?: ChatMsg[]; chatOpen?: boolean }>('/api/events/' + eventId + '/messages');
      setMsgs(c.messages || []);
      if (typeof c.chatOpen === 'boolean') {
        setChatOpen(c.chatOpen);
      }
      setErr('');
    } catch (e) {
      setErr(errMsg(e));
    }
  }

  useEffect(() => {
    void load();
    const poll = window.setInterval(() => { void load(); }, 15000);
    return () => window.clearInterval(poll);
    /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, [eventId]);

  async function send() {
    const v = text.trim();
    if (!v || !chatOpen) return;
    setText('');
    try {
      await api('/api/events/' + eventId + '/messages', { method: 'POST', body: JSON.stringify({ content: v }) });
      await load();
    } catch (e) {
      setText(v);
      alert(errMsg(e));
    }
  }

  let lastDay = '';
  return (
    <div>
      <button className="mg-back" onClick={onBack}>← Back to Event</button>
      <div className="card">
        <h3>💬 Event Chat</h3>
        <p className="sub">Chat with members who have registered for {title || 'this event'}.</p>
        {err && <div className="mg-err">{err}</div>}
        <div className="chatgrid">
          <div className="card" style={{ margin: 0 }}>
            <h3>Registered Members ({members.length})</h3>
            {members.map((a) => (
              <div className="memrow" key={a.readerId}>
                <span className="face">{a.avatarUrl ? <img src={a.avatarUrl} alt="" /> : initialsOf(a.fullName)}</span>
                <b>{a.fullName}{a.readerId === meId ? ' (You)' : ''}</b>
              </div>
            ))}
            {!members.length && <p className="sub">No registrations yet.</p>}
          </div>
          <div>
            <div className="chatmsgs">
              {msgs.map((m, i) => {
                const day = dayOf(m.createdAt);
                const showDay = day !== lastDay;
                lastDay = day;
                const mine = m.authorId === meId;
                return (
                  <div key={m.id || i} style={{ display: 'contents' }}>
                    {showDay && day ? <div className="dayline"><span>{day}</span></div> : null}
                    <div className={'bubble' + (mine ? ' me' : '')}>
                      {!mine && <div className="who">{m.author}</div>}
                      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', justifyContent: mine ? 'flex-end' : 'flex-start' }}>
                        {!mine && <span className="face" style={{ width: 30, height: 30, fontSize: 12 }}>{initialsOf(m.author)}</span>}
                        <div>
                          <div className="tx">{m.content}</div>
                          <div className="ts">{m.createdAt ? new Date(m.createdAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }) : ''}</div>
                        </div>
                        {mine && <span className="face" style={{ width: 30, height: 30, fontSize: 12 }}>{initialsOf(m.author)}</span>}
                      </div>
                    </div>
                  </div>
                );
              })}
              {!msgs.length && <div className="emptybox">No messages yet — say hello!</div>}
            </div>
            {!chatOpen ? (
              <div className="chat-closed-banner" style={{ margin: '8px 0 10px' }}>
                🔒 Event chat is closed. Discussions were open for 48 hours following the event scheduled time.
              </div>
            ) : null}
            <div className="chatbox" style={{ opacity: chatOpen ? 1 : 0.6 }}>
              <input
                placeholder={chatOpen ? 'Type a message…' : 'Chat is closed (48h window expired)'}
                disabled={!chatOpen}
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && chatOpen) void send();
                }}
              />
              <button disabled={!chatOpen} onClick={() => { if (chatOpen) void send(); }} aria-label="Send">
                ➤
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
