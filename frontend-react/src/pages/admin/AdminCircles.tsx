import { useCallback, useEffect, useState, type ChangeEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, apiBase, clearTokens, getAccess, resolveMedia, setApiBase, type Me } from '../../lib/api';
import { COVER_PLACEHOLDER } from '../../lib/placeholders';
import './AdminCircles.css';
import '../CircleManage.css';
import { ManageArea } from '../CircleManage';
import AdminLayout from '../../components/AdminLayout';

function errMsg(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Request failed';
}

type Circle = {
  id: string;
  name?: string;
  city?: string;
  category?: string;
  description?: string;
  coverImageUrl?: string;
  memberCount?: number;
  status?: string;
  tags?: string[];
  hidden?: boolean;
};

function tagsOf(c: Circle): string[] {
  if (c.tags && c.tags.length) return c.tags.slice(0, 4);
  if (c.category) return [c.category];
  return [];
}

type Organizer = { id: string; name?: string; role?: string };

export default function AdminCircles() {
  const navigate = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [err, setErr] = useState('');
  const [baseUrl, setBaseUrl] = useState(apiBase());

  const [circles, setCircles] = useState<Circle[]>([]);
  const [q, setQ] = useState('');
  const [fStatus, setFStatus] = useState('all');
  const [fSort, setFSort] = useState('name_asc');

  /* Admin manages every circle with the same screen organisers use. */
  const [manageId, setManageId] = useState<string | null>(null);

  const [modal, setModal] = useState(false);
  const [cErr, setCErr] = useState('');
  const [cName, setCName] = useState('');
  const [cCat, setCCat] = useState('');
  const [cCity, setCCity] = useState('');
  const [cDesc, setCDesc] = useState('');
  const [cOrg, setCOrg] = useState('');
  const [cActive, setCActive] = useState(true);
  const [cFile, setCFile] = useState<File | null>(null);
  const [cPreview, setCPreview] = useState('');
  const [orgs, setOrgs] = useState<Organizer[]>([]);

  useEffect(() => {
    let on = true;
    (async () => {
      if (!getAccess()) {
        navigate('/auth?redirect=/circles');
        return;
      }
      try {
        const m = await api<Me>('/api/users/me');
        if (!on) return;
        if (m.role !== 'ADMIN') {
          setErr('Signed in as ' + m.role + ' — admin only.');
          return;
        }
        setMe(m);
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) clearTokens();
        navigate('/auth?redirect=/circles');
      }
    })();
    return () => {
      on = false;
    };
  }, [navigate]);

  const load = useCallback(async () => {
    const p = new URLSearchParams({ search: q, status: fStatus, sort: fSort });
    const data = await api<Circle[]>('/api/circles?' + p.toString());
    // Organisers Lounge is a hidden chat group, not a manage-able circle.
    setCircles(Array.isArray(data) ? data.filter((c) => !c.hidden) : []);
  }, [q, fStatus, fSort]);

  useEffect(() => {
    if (me) void load().catch((e: unknown) => setErr(errMsg(e)));
  }, [me, load]);

  async function openCreate() {
    setModal(true);
    try {
      const o = await api<Organizer[]>('/api/users/organizers');
      setOrgs(Array.isArray(o) ? o : []);
      if (o && o.length && !cOrg) setCOrg(o[0].id);
    } catch (e) {
      setCErr(errMsg(e));
    }
  }

  function onCoverFile(e: ChangeEvent<HTMLInputElement>) {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    setCFile(f);
    setCPreview(URL.createObjectURL(f));
  }

  async function createCircle() {
    setCErr('');
    try {
      if (!cName.trim() || !cCity.trim() || !cDesc.trim() || !cOrg) throw new Error('Fill name, city, description and organiser.');
      const body = {
        name: cName.trim(),
        city: cCity.trim(),
        description: cDesc.trim(),
        organizerId: cOrg,
        category: cCat || null,
        active: cActive,
      };
      const c = await api<{ id: string }>('/api/circles', { method: 'POST', body: JSON.stringify(body) });
      if (cFile) {
        const fd = new FormData();
        fd.append('file', cFile);
        const r = await fetch(apiBase() + '/api/circles/' + c.id + '/cover', {
          method: 'POST',
          headers: { Authorization: 'Bearer ' + (getAccess() || '') },
          body: fd,
        });
        if (!r.ok) throw new Error('Circle created, but cover upload failed: ' + r.status);
      }
      setModal(false);
      await load();
      setManageId(c.id);
    } catch (e) {
      setCErr(errMsg(e));
    }
  }

  return (
    <>
      <AdminLayout
        activeView="circles"
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
        className="admin-circles-app"
      >
        {err && <div className="err">{err}</div>}
          {!manageId ? (
            <div id="listView">
              <h1 className="page">Circles</h1>
              <p className="sub">Manage all reading communities on the platform.</p>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
                <button className="btn" onClick={() => void openCreate()}>
                  + Create New Circle
                </button>
              </div>
              <div className="toolbar">
                <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search circles..." />
                <select value={fStatus} onChange={(e) => setFStatus(e.target.value)}>
                  <option value="all">All Status</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
                <select value={fSort} onChange={(e) => setFSort(e.target.value)}>
                  <option value="name_asc">Name A-Z</option>
                  <option value="name_desc">Name Z-A</option>
                  <option value="newest">Newest</option>
                  <option value="members_desc">Most members</option>
                </select>
              </div>
              <p className="sub">
                {circles.length} Circle{circles.length === 1 ? '' : 's'}
              </p>
              <div className="grid">
                {circles.length ? (
                  circles.map((c) => (
                    <div className="ccard" key={c.id}>
                      <img
                        className="cover"
                        alt={'Cover of ' + (c.name || 'circle')}
                        src={resolveMedia(c.coverImageUrl) || COVER_PLACEHOLDER}
                        onError={(e) => {
                          e.currentTarget.onerror = null;
                          e.currentTarget.src = COVER_PLACEHOLDER;
                        }}
                      />
                      <div className="cb">
                        <h4>{c.name}</h4>
                        <span className={'pill ' + (c.status === 'ACTIVE' ? 'org' : 'reader')}>
                          {c.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </span>
                        <div className="meta">
                          <span>👥 {c.memberCount} members</span>
                          <span>🏷 {c.category || 'General'}</span>
                          {c.city && <span>📍 {c.city}</span>}
                        </div>
                        <p className="desc">{c.description || ''}</p>
                        <div className="tagrow">
                          {tagsOf(c).map((t, i) => (
                            <span key={i}>{t}</span>
                          ))}
                        </div>
                        <button className="btn" style={{ width: '100%', marginTop: 'auto' }} onClick={() => setManageId(c.id)}>
                          Manage Circle →
                        </button>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="empty">
                    <b>No circles found</b>
                    Create one to get started.
                  </div>
                )}
              </div>
              <p className="sub" style={{ textAlign: 'right' }}>
                Showing {circles.length} of {circles.length} circles
              </p>
            </div>
          ) : (
            <div id="manageView">
              {manageId && (
                <ManageArea
                  circleId={manageId}
                  backTo="/circles"
                  onBack={() => {
                    setManageId(null);
                    void load().catch((e: unknown) => setErr(errMsg(e)));
                  }}
                />
              )}
            </div>
          )}
      </AdminLayout>
      {modal && (
        <div
          className="circle-modal-backdrop"
          onClick={(e) => {
            if (e.target === e.currentTarget) setModal(false);
          }}
        >
          <div className="circle-modal-sheet" role="dialog" aria-label="Create new circle">
            <div>
              <h2>CREATE NEW CIRCLE</h2>
              <p className="sub">Set up a new reading circle and appoint an organiser to manage it.</p>
              {cErr && <div className="err">{cErr}</div>}
              <div className="fld">
                <label>Circle Name</label>
                <input value={cName} onChange={(e) => setCName(e.target.value)} maxLength={50} placeholder="Enter circle name" />
                <div className="sub">{cName.length}/50</div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div className="fld">
                  <label>Category</label>
                  <select value={cCat} onChange={(e) => setCCat(e.target.value)}>
                    <option value="">Select a category</option>
                    <option>Tamil Literature</option>
                    <option>Philosophy</option>
                    <option>Science Fiction</option>
                    <option>Poetry</option>
                    <option>History</option>
                  </select>
                </div>
                <div className="fld">
                  <label>City</label>
                  <input value={cCity} onChange={(e) => setCCity(e.target.value)} placeholder="Enter city" />
                </div>
              </div>
              <div className="fld">
                <label>Description</label>
                <textarea value={cDesc} onChange={(e) => setCDesc(e.target.value)} rows={3} maxLength={300} placeholder="Write a short description about this circle, its interests and goals..." />
                <div className="sub">{cDesc.length}/300</div>
              </div>
              <div className="fld">
                <label>Cover Image</label>
                <div className="cover-dropzone">
                  <input type="file" accept=".jpg,.jpeg,.png,.webp" onChange={onCoverFile} />
                  <div className="sub">Recommended size: 1200 × 400 (JPG, PNG)</div>
                </div>
              </div>
              <div className="fld">
                <label>Appoint Organiser</label>
                <select value={cOrg} onChange={(e) => setCOrg(e.target.value)}>
                  {orgs.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name} · {o.role}
                    </option>
                  ))}
                </select>
                <div className="sub">The selected user will be the organiser of this circle.</div>
              </div>
              <div className="fld">
                <label>Circle Settings</label>
                <div className="switch">
                  <input type="checkbox" checked={cActive} onChange={(e) => setCActive(e.target.checked)} />
                  <b>Set circle as active</b>
                </div>
                <div className="sub">Active circles will be visible to users and open for join requests.</div>
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 16 }}>
                <button className="btn ghost" onClick={() => setModal(false)}>
                  Cancel
                </button>
                <button className="btn" onClick={() => void createCircle()}>
                  Create Circle
                </button>
              </div>
            </div>
            <div>
              <b>Preview</b>
              <p className="sub">This is how the circle will appear.</p>
              <div className="card">
                <img className="cover" alt="" src={cPreview} />
                <div className="body">
                  <h4>{cName || 'Circle Name'}</h4>
                  <div className="sub">{cCat || 'Category'}</div>
                  <p className="sub">{cDesc || 'Short description...'}</p>
                  <div className="sub">0 members · 0 events</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
