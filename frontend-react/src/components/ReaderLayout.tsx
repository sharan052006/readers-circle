import { useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { api, ackDemotion, ackPromotion, clearTokens, getAccess, initialsOf, isFreshDemotion, isFreshPromotion, resolveMedia, type Me } from '../lib/api';
import './ReaderLayout.css';

export default function ReaderLayout({
  active,
  children,
  search,
  syncNote,
}: {
  active: 'dashboard' | 'circles' | 'events' | 'books' | 'recommend' | 'chat' | 'settings';
  children: ReactNode;
  search?: ReactNode;
  syncNote?: string;
}) {
  const nav = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [unread, setUnread] = useState(0);
  const [cartCount, setCartCount] = useState(0);
  const [meOpen, setMeOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  const [notifs, setNotifs] = useState<Array<{ title: string; body?: string }>>([]);
  const [sync, setSync] = useState('Connecting…');
  const [notice, setNotice] = useState<null | { kind: 'promoted' | 'demoted'; userId: string }>(null);
  const topbarRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!bellOpen && !meOpen) return;
    function handlePointerDown(e: MouseEvent | TouchEvent) {
      if (topbarRef.current && !topbarRef.current.contains(e.target as Node)) {
        setBellOpen(false);
        setMeOpen(false);
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setBellOpen(false);
        setMeOpen(false);
      }
    }
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [bellOpen, meOpen]);

  // Role-transition guard (DB-fresh /me, mount + poll): a freshly promoted
  // ORGANIZER sees congratulations once; a freshly demoted READER (was an
  // organiser) sees the demotion notice once. Both force re-login — the
  // backend revoked their refresh sessions on role change.
  useEffect(() => {
    if (!getAccess() || notice) return;
    let on = true;
    async function checkRole() {
      try {
        const m = await api<Me>('/api/users/me');
        if (!on) return;
        if (isFreshPromotion(m.role, m.id)) setNotice({ kind: 'promoted', userId: m.id || '' });
        else if (isFreshDemotion(m.role, m.id)) setNotice({ kind: 'demoted', userId: m.id || '' });
      } catch {
        /* page-level handlers deal with 401s; never block on check failure */
      }
    }
    void checkRole();
    const poll = window.setInterval(() => {
      if (!on) return;
      void checkRole();
    }, 20000);
    return () => {
      on = false;
      window.clearInterval(poll);
    };
  }, [nav, syncNote, notice]);

  function returnToLogin() {
    if (notice?.userId) {
      if (notice.kind === 'promoted') ackPromotion(notice.userId);
      else ackDemotion(notice.userId);
    }
    clearTokens();
    nav('/auth');
  }

  useEffect(() => {
    if (!getAccess()) {
      nav('/auth');
      return;
    }
    let on = true;
    (async () => {
      try {
        const m = await api<Me>('/api/users/me');
        if (on) setMe(m);
      } catch (e: unknown) {
        if (e && typeof e === 'object' && 'status' in e && (e as { status: number }).status === 401) nav('/auth');
      }
      try {
        const u = await api<{ unreadCount?: number }>('/api/notifications/unread-count');
        if (on) setUnread(Number(u.unreadCount || 0));
      } catch {
        /* ignore */
      }
      try {
        const c = await api<{ count?: number }>('/api/cart');
        if (on) setCartCount(Number(c.count || 0));
      } catch {
        /* ignore */
      }
      if (on) setSync(syncNote || 'Synced with admin just now');
    })();
    const onCart = () => {
      api<{ count?: number }>('/api/cart')
        .then((c) => setCartCount(Number(c.count || 0)))
        .catch(() => undefined);
    };
    window.addEventListener('cart-changed', onCart);
    return () => {
      on = false;
      window.removeEventListener('cart-changed', onCart);
    };
  }, [nav, syncNote]);

  async function openBell() {
    const next = !bellOpen;
    setBellOpen(next);
    setMeOpen(false);
    if (!next) return;
    try {
      const list = await api<Array<{ title: string; body?: string }>>('/api/notifications');
      setNotifs(list.slice(0, 6));
      try {
        const u = await api<{ unreadCount?: number }>('/api/notifications/unread-count');
        setUnread(Number(u.unreadCount || 0));
      } catch {
        /* ignore */
      }
    } catch {
      setNotifs([]);
    }
  }

  async function markAll() {
    try {
      await api('/api/notifications/read-all', { method: 'POST' });
      setUnread(0);
      setBellOpen(false);
    } catch {
      /* ignore */
    }
  }

  const linkCls = ({ isActive }: { isActive: boolean }) => (isActive ? 'rnav-a on' : 'rnav-a');

  return (
    <div className="rapp">
      <aside className="rrail" aria-label="Reader navigation">
        <div className="rbrand">
          <span className="rmark">📖</span>
          <b>Reader's Circle</b>
        </div>
        <nav className="rnav">
          <NavLink to="/reader" className={active === 'dashboard' ? 'rnav-a on' : linkCls}>
            <span>⌂</span> Dashboard
          </NavLink>
          <NavLink to="/reader-circles" className={active === 'circles' ? 'rnav-a on' : linkCls}>
            <span>👥</span> Circles
          </NavLink>
          <NavLink to="/reader-events" className={active === 'events' ? 'rnav-a on' : linkCls}>
            <span>📅</span> Events
          </NavLink>
          <NavLink to="/reader-books" className={active === 'books' ? 'rnav-a on' : linkCls}>
            <span>📚</span> Books
          </NavLink>
          <NavLink to="/reader-recommendations" className={active === 'recommend' ? 'rnav-a on' : linkCls}>
            <span>⭐</span> Recommendations
          </NavLink>
          <NavLink to="/reader-chat" className={active === 'chat' ? 'rnav-a on' : linkCls}>
            <span>💬</span> Chat
          </NavLink>
          <NavLink to="/reader-settings" className={active === 'settings' ? 'rnav-a on' : linkCls}>
            <span>⚙</span> Settings
          </NavLink>
        </nav>
        {active !== 'dashboard' && (
          <div className="rsyncnote">
            Same data as admin.
            <br />
            <b>{sync}</b>
          </div>
        )}
      </aside>
      <div className="rmain">
        <div className="rtopbar" ref={topbarRef}>
          <div className="rsearch">{search}</div>
          <div className="rtopright">
            <button
              className="rbell"
              onClick={openBell}
              aria-label="Notifications"
              aria-haspopup="true"
              aria-expanded={bellOpen}
            >
              <span aria-hidden="true">🔔</span>
              {unread > 0 && <span className="rdot" />}
              {unread > 0 && <span className="rcount">{unread}</span>}
            </button>
            <button className="rbell" onClick={() => nav('/reader-cart')} aria-label="Cart">
              <span aria-hidden="true">🛒</span>
              {cartCount > 0 && <span className="rcount">{cartCount}</span>}
            </button>
            <button
              className="ruserchip"
              onClick={() => {
                setMeOpen(!meOpen);
                setBellOpen(false);
              }}
              aria-haspopup="true"
              aria-expanded={meOpen}
              aria-label="User account menu"
            >
              <span className="rface">
                <span>{initialsOf(me?.name || me?.email)}</span>
                {me?.profilePictureUrl && (
                  <img
                    src={resolveMedia(me.profilePictureUrl)}
                    alt=""
                    onError={(e) => {
                      e.currentTarget.style.display = 'none';
                    }}
                  />
                )}
              </span>
              <b>{me?.name || me?.email || 'Reader'}</b>
              <span aria-hidden="true">▾</span>
            </button>
          </div>
        </div>
        {bellOpen && (
          <div className="rdrop" style={{ minWidth: 320, maxWidth: 360 }}>
            {notifs.length ? (
              <>
                {notifs.map((n, i) => (
                  <div key={i} style={{ padding: '9px 11px', borderBottom: '1px solid #F0EAD9' }}>
                    <b style={{ fontSize: 13 }}>{n.title}</b>
                    <div style={{ fontSize: 12, color: '#6B6257' }}>{n.body || ''}</div>
                  </div>
                ))}
                <button
                  style={{ border: 0, background: 'none', color: '#2F5D50', fontWeight: 700, padding: 9, cursor: 'pointer' }}
                  onClick={markAll}
                >
                  Mark all read
                </button>
              </>
            ) : (
              <div style={{ padding: 12, fontSize: 13, color: '#6B6257' }}>No notifications.</div>
            )}
          </div>
        )}
        {meOpen && (
          <div className="rdrop">
            <button
              onClick={() => {
                setMeOpen(false);
                nav('/reader-settings');
              }}
            >
              Settings
            </button>
            <button
              onClick={() => {
                clearTokens();
                nav('/auth');
              }}
            >
              Sign out
            </button>
          </div>
        )}
        {children}
        {notice && (
          <div
            style={{
              position: 'fixed',
              inset: 0,
              zIndex: 9999,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: '#FAF6EE',
              padding: 24,
            }}
          >
            <div style={{ textAlign: 'center', maxWidth: 520 }}>
              <h1 style={{ fontFamily: 'Georgia, serif', color: '#6B2A1F', marginBottom: 12 }}>
                {notice.kind === 'promoted'
                  ? 'Congratulations, you have been promoted to organiser!'
                  : 'You have been demoted to reader'}
              </h1>
              <p style={{ color: '#5c554b', marginBottom: 24 }}>
                {notice.kind === 'promoted'
                  ? 'Your reader access is now locked. Please return to the login page and sign in again to continue to your organiser screen.'
                  : 'Your organiser access is now locked. Please return to the login page and sign in again to continue to the reader screens.'}
              </p>
              <button
                type="button"
                onClick={returnToLogin}
                style={{
                  background: '#6B2A1F',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 8,
                  padding: '10px 22px',
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                Return to login page
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
