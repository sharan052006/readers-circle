import { useEffect, useRef, useState, type ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { api, apiUrl, ackDemotion, ackPromotion, clearTokens, getAccess, initialsOf, isFreshDemotion, isFreshPromotion, resolveMedia, type Me } from '../lib/api';
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
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [rightPanelOpen, setRightPanelOpen] = useState(false);
  const topbarRef = useRef<HTMLDivElement>(null);
  const [headerHeight, setHeaderHeight] = useState<number>(54);

  // Close mobile drawers on route changes
  useEffect(() => {
    setMobileMenuOpen(false);
    setRightPanelOpen(false);
  }, [active]);

  // Dynamically track topbar height so drawer and backdrop sit directly below the header
  useEffect(() => {
    const updateHeaderHeight = () => {
      if (topbarRef.current) {
        setHeaderHeight(topbarRef.current.offsetHeight);
      }
    };
    updateHeaderHeight();
    window.addEventListener('resize', updateHeaderHeight);
    return () => window.removeEventListener('resize', updateHeaderHeight);
  }, [search]);

  // Close mobile drawers on Escape key & lock body scroll without layout shift
  useEffect(() => {
    if (!mobileMenuOpen && !rightPanelOpen) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setMobileMenuOpen(false);
        setRightPanelOpen(false);
      }
    };
    document.addEventListener('keydown', handleKey);
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
    const prevOverflow = document.body.style.overflow;
    const prevPaddingRight = document.body.style.paddingRight;
    document.body.style.overflow = 'hidden';
    if (scrollbarWidth > 0) {
      document.body.style.paddingRight = `${scrollbarWidth}px`;
    }
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = prevOverflow;
      document.body.style.paddingRight = prevPaddingRight;
    };
  }, [mobileMenuOpen, rightPanelOpen]);

  const hasRightPanel =
    active === 'dashboard' ||
    active === 'circles' ||
    active === 'events' ||
    active === 'recommend' ||
    active === 'chat';

  const rightPanelTitle =
    active === 'circles'
      ? 'Circle Roles'
      : active === 'events'
      ? 'Calendar & Filters'
      : active === 'recommend'
      ? 'Reading Picks'
      : active === 'chat'
      ? 'Circle Information'
      : 'Page Highlights';

  const rightPanelIcon =
    active === 'circles'
      ? '👥'
      : active === 'events'
      ? '📅'
      : active === 'recommend'
      ? '⭐'
      : active === 'chat'
      ? '💬'
      : '📑';

  // Edge-swipe navigation (Instagram-style) for mobile drawer
  useEffect(() => {
    const isMobile = () =>
      window.matchMedia('(max-width: 800px)').matches || window.innerWidth <= 800;

    let startX = 0;
    let startY = 0;
    let lastX = 0;
    let lastY = 0;
    let isTracking = false;
    let isCanceled = false;
    let leftDrawerOpenAtStart = false;
    let rightPanelOpenAtStart = false;

    const onTouchStart = (e: TouchEvent) => {
      if (!isMobile() || e.touches.length !== 1) return;
      const touch = e.touches[0];
      const target = e.target;
      const elem = target instanceof Element ? target : (target as Node)?.parentElement;

      // Prevent triggering when interacting with form controls or carousels
      if (
        elem &&
        elem.closest(
          'input, textarea, select, button, form, .rail-cards-track, [data-no-swipe], [role="slider"]'
        )
      ) {
        return;
      }

      leftDrawerOpenAtStart = mobileMenuOpen;
      rightPanelOpenAtStart = rightPanelOpen;

      if (leftDrawerOpenAtStart) {
        const isDrawerOrBackdrop = elem && elem.closest('#rmobile-drawer, .rmobile-backdrop');
        if (!isDrawerOrBackdrop) return;
      }

      if (rightPanelOpenAtStart) {
        const isRightOrBackdrop = elem && elem.closest('.railcards, .rdet-rail, aside, .rec-side, .gchat-right, .rmobile-right-backdrop, .rmobile-right-drawer-header');
        if (!isRightOrBackdrop) return;
      }

      startX = touch.clientX;
      startY = touch.clientY;
      lastX = touch.clientX;
      lastY = touch.clientY;
      isTracking = true;
      isCanceled = false;
    };

    const onTouchMove = (e: TouchEvent) => {
      if (!isTracking || isCanceled || e.touches.length !== 1) return;
      const touch = e.touches[0];
      lastX = touch.clientX;
      lastY = touch.clientY;
      const dx = lastX - startX;
      const dy = lastY - startY;
      const absX = Math.abs(dx);
      const absY = Math.abs(dy);

      // Cancel if user is scrolling vertically (vertical movement significantly dominates)
      if (absY > 20 && absY > absX * 1.4) {
        isCanceled = true;
        return;
      }

      if (absX >= 30 && absX > absY) {
        if (leftDrawerOpenAtStart) {
          // Left nav open: swiping dismisses it
          if (absX >= 25) {
            setMobileMenuOpen(false);
            isCanceled = true;
            isTracking = false;
          }
        } else if (rightPanelOpenAtStart) {
          // Right panel open: swiping dismisses it
          if (absX >= 25) {
            setRightPanelOpen(false);
            isCanceled = true;
            isTracking = false;
          }
        } else {
          // Both closed:
          // User: "swiping , left should get me the side nav bar on left , right should get me the things on the right"
          if (dx <= -30) {
            // Swiping LEFT -> opens side nav bar on left
            setMobileMenuOpen(true);
            setRightPanelOpen(false);
            isCanceled = true;
            isTracking = false;
          } else if (dx >= 30) {
            // Swiping RIGHT -> opens things on the right
            if (hasRightPanel) {
              setRightPanelOpen(true);
              setMobileMenuOpen(false);
              isCanceled = true;
              isTracking = false;
            }
          }
        }
      }
    };

    const onTouchEnd = (e: TouchEvent) => {
      if (!isTracking || isCanceled) {
        isTracking = false;
        isCanceled = false;
        return;
      }

      if (e.changedTouches && e.changedTouches.length > 0) {
        lastX = e.changedTouches[0].clientX;
        lastY = e.changedTouches[0].clientY;
      }

      const dx = lastX - startX;
      const dy = lastY - startY;
      const absX = Math.abs(dx);
      const absY = Math.abs(dy);

      if (absX >= 30 && absX > absY) {
        if (leftDrawerOpenAtStart) {
          if (absX >= 25) setMobileMenuOpen(false);
        } else if (rightPanelOpenAtStart) {
          if (absX >= 25) setRightPanelOpen(false);
        } else {
          if (dx <= -30) {
            // Swiping LEFT -> opens side nav bar on left
            setMobileMenuOpen(true);
            setRightPanelOpen(false);
          } else if (dx >= 30 && hasRightPanel) {
            // Swiping RIGHT -> opens things on the right
            setRightPanelOpen(true);
            setMobileMenuOpen(false);
          }
        }
      }

      isTracking = false;
      isCanceled = false;
    };

    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: true });
    window.addEventListener('touchend', onTouchEnd, { passive: true });
    window.addEventListener('touchcancel', onTouchEnd, { passive: true });

    return () => {
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('touchend', onTouchEnd);
      window.removeEventListener('touchcancel', onTouchEnd);
    };
  }, [mobileMenuOpen, rightPanelOpen, hasRightPanel]);

  async function handleSignOut() {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    setMeOpen(false);
    try {
      await fetch(apiUrl('/api/auth/logout'), {
        method: 'POST',
        headers: {
          ...(getAccess() ? { Authorization: 'Bearer ' + getAccess() } : {}),
        },
      }).catch(() => null);
    } catch {
      /* ignore server-side logout failure */
    } finally {
      clearTokens();
      setMe(null);
      setIsLoggingOut(false);
      nav('/auth', { replace: true });
    }
  }

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
    <div
      className={'rapp' + (rightPanelOpen ? ' r-right-open' : '')}
      style={{ '--rh-top': `${headerHeight}px` } as React.CSSProperties}
    >
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
        {mobileMenuOpen && (
          <div
            className="rmobile-backdrop"
            style={{ top: `${headerHeight}px` }}
            onClick={() => setMobileMenuOpen(false)}
            aria-hidden="true"
          />
        )}
        {rightPanelOpen && (
          <div
            className="rmobile-right-backdrop"
            style={{ top: `${headerHeight}px` }}
            onClick={() => setRightPanelOpen(false)}
            aria-hidden="true"
          />
        )}
        {rightPanelOpen && hasRightPanel && (
          <div
            className="rmobile-right-drawer-header"
            style={{ top: `${headerHeight}px` }}
          >
            <div className="rmobile-rdh-title">
              <span aria-hidden="true">{rightPanelIcon}</span>
              <b>{rightPanelTitle}</b>
            </div>
            <button
              type="button"
              className="rmobile-rdh-close"
              onClick={() => setRightPanelOpen(false)}
              aria-label={`Close ${rightPanelTitle}`}
            >
              ✕
            </button>
          </div>
        )}
        <div
          id="rmobile-drawer"
          className={'rmobile-drawer' + (mobileMenuOpen ? ' open' : '')}
          style={{ top: `${headerHeight}px`, height: `calc(100vh - ${headerHeight}px)` }}
          aria-label="Mobile navigation"
          aria-hidden={!mobileMenuOpen}
        >
          <nav className="rmobile-nav">
            <NavLink
              to="/reader"
              className={active === 'dashboard' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">⌂</span>
              <span>Dashboard</span>
            </NavLink>
            <NavLink
              to="/reader-circles"
              className={active === 'circles' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">👥</span>
              <span>Circles</span>
            </NavLink>
            <NavLink
              to="/reader-events"
              className={active === 'events' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">📅</span>
              <span>Events</span>
            </NavLink>
            <NavLink
              to="/reader-books"
              className={active === 'books' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">📚</span>
              <span>Books</span>
            </NavLink>
            <NavLink
              to="/reader-recommendations"
              className={active === 'recommend' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">⭐</span>
              <span>Recommendations</span>
            </NavLink>
            <NavLink
              to="/reader-chat"
              className={active === 'chat' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">💬</span>
              <span>Chat</span>
            </NavLink>
            <NavLink
              to="/reader-settings"
              className={active === 'settings' ? 'rmobile-nav-a on' : 'rmobile-nav-a'}
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="rmobile-nav-icon" aria-hidden="true">⚙</span>
              <span>Settings</span>
            </NavLink>
          </nav>
          <div className="rmobile-drawer-foot">
            <div className="rmobile-userinfo">
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
              <div className="rmobile-usernames">
                <b>{me?.name || me?.email || 'Reader'}</b>
                <small>{me?.role === 'ORGANIZER' ? 'Organiser' : me?.role === 'ADMIN' ? 'Admin' : 'Reader'}</small>
              </div>
            </div>
            <button
              type="button"
              className="rmobile-signout-btn"
              onClick={handleSignOut}
              disabled={isLoggingOut}
            >
              {isLoggingOut ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        </div>

        <div className="rtopbar" ref={topbarRef}>
          <div className="rmobile-bar">
            <button
              type="button"
              id="rmobile-drawer-toggle"
              className="rmobile-toggle"
              onClick={() => {
                setMobileMenuOpen(!mobileMenuOpen);
                setBellOpen(false);
                setMeOpen(false);
              }}
              aria-label={mobileMenuOpen ? 'Close navigation drawer' : 'Open navigation drawer'}
              aria-expanded={mobileMenuOpen}
              aria-controls="rmobile-drawer"
              title={mobileMenuOpen ? 'Close navigation drawer' : 'Open navigation drawer'}
            >
              {mobileMenuOpen ? (
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.75"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <polyline points="15 18 9 12 15 6" />
                </svg>
              ) : (
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.75"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <polyline points="9 18 15 12 9 6" />
                </svg>
              )}
            </button>
            <NavLink to="/reader" className="rmobile-brand" aria-label="Reader's Circle">
              <span className="rmark">📖</span>
              <b>Reader's Circle</b>
            </NavLink>
          </div>
          {search ? <div className="rsearch">{search}</div> : null}
          <div className="rtopright">
            {hasRightPanel && (
              <button
                type="button"
                className={'rbell rmobile-right-toggle' + (rightPanelOpen ? ' active' : '')}
                onClick={() => {
                  setRightPanelOpen(!rightPanelOpen);
                  setMobileMenuOpen(false);
                  setBellOpen(false);
                  setMeOpen(false);
                }}
                aria-label={rightPanelOpen ? `Close ${rightPanelTitle}` : `Open ${rightPanelTitle}`}
                aria-expanded={rightPanelOpen}
                title={rightPanelOpen ? `Close ${rightPanelTitle}` : `Open ${rightPanelTitle}`}
              >
                <span aria-hidden="true">{rightPanelIcon}</span>
              </button>
            )}
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
            {bellOpen && (
              <div
                className="rdrop"
                style={{ minWidth: 320, maxWidth: 360 }}
                onMouseDown={(e) => e.stopPropagation()}
                onTouchStart={(e) => e.stopPropagation()}
              >
                {notifs.length ? (
                  <>
                    {notifs.map((n, i) => (
                      <div key={i} style={{ padding: '9px 11px', borderBottom: '1px solid #F0EAD9' }}>
                        <b style={{ fontSize: 13 }}>{n.title}</b>
                        <div style={{ fontSize: 12, color: '#6B6257' }}>{n.body || ''}</div>
                      </div>
                    ))}
                    <button
                      type="button"
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
              <div
                className="rdrop"
                onMouseDown={(e) => e.stopPropagation()}
                onTouchStart={(e) => e.stopPropagation()}
              >
                <button
                  type="button"
                  onClick={() => {
                    setMeOpen(false);
                    nav('/reader-settings');
                  }}
                >
                  Settings
                </button>
                <button
                  type="button"
                  onClick={handleSignOut}
                  disabled={isLoggingOut}
                >
                  {isLoggingOut ? 'Signing out…' : 'Sign out'}
                </button>
              </div>
            )}
          </div>
        </div>
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
