import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ReaderLayout from '../components/ReaderLayout';
import { api, apiBase, ApiError, clearTokens, getAccess, initialsOf, resolveMedia, type Me } from '../lib/api';
import './ReaderSettings.css';

type PrefKey = 'joinRequest' | 'memberJoined' | 'eventCreated' | 'discussion' | 'bookRecommendation' | 'chatMessage' | 'replies';

type Prefs = Partial<Record<PrefKey, boolean>>;

type NotifDef = {
  key: PrefKey;
  icon: string;
  t: string;
  s: string;
};

const NOTIFS: NotifDef[] = [
  { key: 'joinRequest', icon: '👥', t: 'New join request', s: 'Notify when your request to join a circle is accepted or rejected.' },
  { key: 'memberJoined', icon: '👤', t: 'New member joined', s: 'Notify when a new member joins a circle you\u2019re part of.' },
  { key: 'eventCreated', icon: '📅', t: 'New event created', s: 'Notify when an organizer creates a new event in your circle.' },
  { key: 'discussion', icon: '💬', t: 'New discussion', s: 'Notify when a member starts a new discussion in your circle.' },
  { key: 'bookRecommendation', icon: '📖', t: 'New book recommendation', s: 'Notify when a member creates a new book recommendation.' },
  { key: 'chatMessage', icon: '💬', t: 'Chat messages', s: 'Notify when you receive new messages in your circle chats.' },
  { key: 'replies', icon: '↩', t: 'New replies', s: 'Notify when someone replies to your discussions or comments.' },
];

function formatRole(role?: string): string {
  const r = role || 'READER';
  return r.charAt(0) + r.slice(1).toLowerCase();
}

export default function ReaderSettings() {
  const nav = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('');
  const [photoUrl, setPhotoUrl] = useState('');
  const [prefs, setPrefs] = useState<Prefs>({});
  const [pageErr, setPageErr] = useState('');
  const [profileSaved, setProfileSaved] = useState('');
  const [photoSaved, setPhotoSaved] = useState('');
  const [imgFailed, setImgFailed] = useState(false);
  const [sync, setSync] = useState('Connecting…');
  const [pwOpen, setPwOpen] = useState(false);
  const [pwCur, setPwCur] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwErr, setPwErr] = useState('');

  useEffect(() => {
    let on = true;
    (async () => {
      setSync('Syncing…');
      try {
        const me = await api<Me>('/api/users/me');
        if (!on) return;
        setName(me.name || '');
        setEmail(me.email || '');
        setRole(formatRole(me.role));
        setPhotoUrl(me.profilePictureUrl || '');
        let p: Prefs = {};
        try {
          p = await api<Prefs>('/api/users/me/notification-preferences');
        } catch {
          p = {};
        }
        if (!on) return;
        setPrefs(p || {});
        setSync('Synced just now');
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) {
          nav('/auth');
          return;
        }
        setPageErr(e instanceof Error ? e.message : 'Failed to load settings');
        setSync('Offline');
      }
    })();
    return () => {
      on = false;
    };
  }, [nav]);

  useEffect(() => {
    if (!pwOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setPwOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [pwOpen]);

  async function saveName() {
    const v = name.trim();
    if (!v) return;
    try {
      await api('/api/users/me', { method: 'PATCH', body: JSON.stringify({ name: v }) });
      setProfileSaved('Saved ✓');
      window.setTimeout(() => setProfileSaved(''), 2000);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        nav('/auth');
        return;
      }
      setPageErr(e instanceof Error ? e.message : 'Save failed');
    }
  }

  async function onPrefChange(key: PrefKey, checked: boolean) {
    setPrefs((prev) => ({ ...prev, [key]: checked }));
    try {
      const patch: Prefs = {};
      patch[key] = checked;
      await api('/api/users/me/notification-preferences', { method: 'PUT', body: JSON.stringify(patch) });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        nav('/auth');
        return;
      }
      setPageErr(e instanceof Error ? e.message : 'Failed to save preference');
      setPrefs((prev) => ({ ...prev, [key]: !checked }));
    }
  }

  async function onPhoto(file: File | undefined) {
    if (!file) return;
    try {
      const fd = new FormData();
      fd.append('file', file);
      const r = await fetch(apiBase() + '/api/users/me/photo', {
        method: 'POST',
        headers: getAccess() ? { Authorization: 'Bearer ' + getAccess() } : {},
        body: fd,
      });
      if (r.status === 401) {
        nav('/auth');
        return;
      }
      if (!r.ok) {
        let d: { message?: string; error?: string } | null = null;
        try {
          d = (await r.json()) as { message?: string; error?: string };
        } catch {
          d = null;
        }
        throw new Error((d && (d.message || d.error)) || `Upload failed: ${r.status}`);
      }
      const u = (await r.json()) as { profilePictureUrl?: string };
      if (u.profilePictureUrl) setPhotoUrl(u.profilePictureUrl);
      setPhotoSaved('Photo updated ✓');
      window.setTimeout(() => setPhotoSaved(''), 2000);
      try {
        const me = await api<Me>('/api/users/me');
        setPhotoUrl(me.profilePictureUrl || u.profilePictureUrl || '');
        setName(me.name || '');
      } catch {
        /* keep local state */
      }
    } catch (e) {
      setPageErr(e instanceof Error ? e.message : 'Upload failed');
    }
  }

  async function changePassword() {
    setPwErr('');
    if (!pwCur || !pwNew) {
      setPwErr('Enter current and new password.');
      return;
    }
    try {
      await api('/api/users/me/password', {
        method: 'POST',
        body: JSON.stringify({ currentPassword: pwCur, newPassword: pwNew }),
      });
      setPwOpen(false);
      setPwCur('');
      setPwNew('');
      setProfileSaved('Password updated ✓');
    } catch (e) {
      setPwErr(e instanceof Error ? e.message : 'Password update failed');
    }
  }

  async function signOutAll() {
    if (!window.confirm('Sign out from all devices?')) return;
    try {
      await api('/api/users/me/sign-out-all', { method: 'POST' });
      clearTokens();
      nav('/auth');
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        clearTokens();
        nav('/auth');
        return;
      }
      setPageErr(e instanceof Error ? e.message : 'Sign out failed');
    }
  }

  return (
    <ReaderLayout
      active="settings"
      syncNote={sync}
    >
      <div className="rset-wrap">
        <h1 className="page">Settings</h1>
        <p className="sub">Manage your account and notification preferences.</p>
        {pageErr && <div className="err">{pageErr}</div>}

        <section className="card" aria-labelledby="tProfile">
          <h2 id="tProfile">Profile</h2>
          <p className="csub">Manage your account details.</p>
          <div className="profile-grid">
            <div className="photo-col">
              <div className="avatar-wrap">
                <div className="bigav">
                  {photoUrl && !imgFailed ? (
                    <img
                      src={resolveMedia(photoUrl)}
                      alt={'Profile photo of ' + (name || email || 'reader')}
                      onError={() => setImgFailed(true)}
                    />
                  ) : (
                    initialsOf(name || email) || 'R'
                  )}
                </div>
                <span className="cambadge" aria-hidden="true">📷</span>
              </div>
              <div>
                <label className="photobtn" style={{ display: 'inline-block', cursor: 'pointer' }}>
                  📷 Change Photo
                  <input
                    type="file"
                    accept="image/*"
                    hidden
                    onChange={(e) => {
                      const f = e.target.files && e.target.files[0];
                      setImgFailed(false);
                      void onPhoto(f || undefined);
                      e.target.value = '';
                    }}
                  />
                </label>
              </div>
              {photoSaved && <div className="saved">{photoSaved}</div>}
            </div>
            <div className="fields">
              <div className="frow">
                <label htmlFor="fName">Name</label>
                <input
                  id="fName"
                  autoComplete="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') void saveName();
                  }}
                />
              </div>
              <div className="frow">
                <label htmlFor="fEmail">Email</label>
                <input id="fEmail" disabled value={email} />
              </div>
              <div className="frow">
                <label htmlFor="fRole">Role</label>
                <input id="fRole" disabled value={role} />
              </div>
              <div className="profile-actions">
                <div />
                <div className="action-row">
                  <button className="save-btn" type="button" onClick={() => void saveName()}>
                    Save Changes
                  </button>
                  {profileSaved && (
                    <span className="saved-badge" aria-live="polite">
                      {profileSaved}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="card" aria-labelledby="tNotif">
          <h2 id="tNotif">Notification Settings</h2>
          <p className="csub">Choose what notifications you want to receive.</p>
          <div>
            {NOTIFS.map((n) => {
              const on = prefs[n.key] !== false;
              return (
                <div key={n.key} className="nrow">
                  <span className="nicon">{n.icon}</span>
                  <div>
                    <b>{n.t}</b>
                    <small>{n.s}</small>
                  </div>
                  <label className="tgl">
                    <input
                      type="checkbox"
                      checked={on}
                      aria-label={n.t}
                      onChange={(e) => void onPrefChange(n.key, e.target.checked)}
                    />
                    <i></i>
                  </label>
                </div>
              );
            })}
          </div>
        </section>

        <section className="card" aria-labelledby="tSec">
          <h2 id="tSec">Security Settings</h2>
          <p className="csub">Manage your account security.</p>
          <div className="srow">
            <span className="nicon">🔑</span>
            <div>
              <b>Change password</b>
              <small>Update your account password.</small>
            </div>
            <button className="sbtn" type="button" onClick={() => setPwOpen(true)}>
              🔑 Change Password
            </button>
          </div>
          <div className="srow">
            <span className="nicon">💳</span>
            <div>
              <b>Sign out of all devices</b>
              <small>Sign out from all devices where your account is currently logged in.</small>
            </div>
            <button className="sbtn" type="button" onClick={() => void signOutAll()}>
              ⏏ Sign Out All
            </button>
          </div>
        </section>
      </div>

      {pwOpen && (
        <div
          className="veil"
          onClick={(e) => {
            if (e.target === e.currentTarget) setPwOpen(false);
          }}
        >
          <div className="modal" role="dialog" aria-label="Change password">
            <h2>Change password</h2>
            <p className="csub" style={{ color: 'var(--muted)', fontSize: 13, margin: 0 }}>
              Minimum 8 characters.
            </p>
            {pwErr && (
              <div className="err" style={{ marginTop: 10 }}>
                {pwErr}
              </div>
            )}
            <label htmlFor="pwCur">Current password</label>
            <input id="pwCur" type="password" autoComplete="current-password" value={pwCur} onChange={(e) => setPwCur(e.target.value)} />
            <label htmlFor="pwNew">New password</label>
            <input id="pwNew" type="password" autoComplete="new-password" value={pwNew} onChange={(e) => setPwNew(e.target.value)} />
            <div className="row">
              <button className="btn ghost" type="button" onClick={() => setPwOpen(false)}>
                Cancel
              </button>
              <button className="btn" type="button" onClick={() => void changePassword()}>
                Update
              </button>
            </div>
          </div>
        </div>
      )}
    </ReaderLayout>
  );
}
