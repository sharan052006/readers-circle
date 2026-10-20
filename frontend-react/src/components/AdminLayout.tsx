import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { initialsOf, type Me } from '../lib/api';
import './AdminLayout.css';

export type AdminViewKey =
  | 'dashboard'
  | 'circles'
  | 'events'
  | 'chat'
  | 'discussions'
  | 'recommend'
  | 'books'
  | 'orders'
  | 'usershome'
  | 'analytics'
  | 'settings';

export interface AdminLayoutProps {
  activeView: AdminViewKey;
  onNavigateView?: (view: AdminViewKey) => void;
  syncNote?: string;
  baseUrl?: string;
  setBaseUrl?: (url: string) => void;
  me?: Me | null;
  signOut?: () => void;
  children: ReactNode;
  className?: string;
}

const NAV_ITEMS: Array<{
  key: AdminViewKey;
  label: string;
  icon: string;
  group?: string;
}> = [
  { key: 'dashboard', label: 'Dashboard', icon: '🏠' },
  { key: 'circles', label: 'Circles', icon: '👥' },
  { key: 'events', label: 'Events', icon: '📅' },
  { key: 'chat', label: 'General Chat', icon: '💬', group: 'Community' },
  { key: 'discussions', label: 'Book Discussions', icon: '📖', group: 'Community' },
  { key: 'recommend', label: 'Book Recommendations', icon: '⭐', group: 'Community' },
  { key: 'books', label: 'Books', icon: '📚', group: 'Commerce' },
  { key: 'orders', label: 'Orders', icon: '🛒', group: 'Commerce' },
  { key: 'usershome', label: 'Users', icon: '👤', group: 'Users' },
  { key: 'analytics', label: 'Analytics', icon: '📊', group: 'Platform' },
  { key: 'settings', label: 'Settings', icon: '⚙️', group: 'Platform' },
];

export default function AdminLayout({
  activeView,
  onNavigateView,
  syncNote = 'Real-Time Sync Active',
  baseUrl,
  setBaseUrl,
  me,
  signOut,
  children,
  className = '',
}: AdminLayoutProps) {
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close drawers on Escape & lock body scroll without shifting
  useEffect(() => {
    if (!mobileMenuOpen) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMobileMenuOpen(false);
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
  }, [mobileMenuOpen]);

  // Close user dropdown on outside click
  useEffect(() => {
    if (!userDropdownOpen) return;
    const handleClick = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setUserDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [userDropdownOpen]);

  const handleNavClick = (key: AdminViewKey) => {
    setMobileMenuOpen(false);
    if (onNavigateView) {
      onNavigateView(key);
    } else {
      if (key === 'dashboard') navigate('/admin');
      else if (key === 'circles') navigate('/circles');
      else if (key === 'events') navigate('/events');
      else navigate(`/admin?view=${key}`);
    }
  };

  const userName = me?.name || me?.email || 'Admin User';
  const userInitials = initialsOf(userName);

  return (
    <div className={`admin-app-layout app ${className}`.trim()}>
      {/* Desktop Sticky Sidebar */}
      <aside className="side admin-desktop-sidebar" aria-label="Admin navigation">
        <div className="brandrow">
          <span className="mark">📚</span>
          <div>
            <b>READER'S CIRCLE</b>
            <span>Admin Panel</span>
          </div>
        </div>

        <nav id="nav" className="admin-nav-list">
          {NAV_ITEMS.map((item, index) => {
            const prevItem = index > 0 ? NAV_ITEMS[index - 1] : null;
            const showGroup = item.group && (!prevItem || prevItem.group !== item.group);
            const isActive = activeView === item.key;
            return (
              <div key={item.key} className="admin-nav-item-wrap">
                {showGroup && <div className="navgroup">{item.group}</div>}
                <button
                  type="button"
                  className="navbtn"
                  data-view={item.key}
                  aria-current={isActive ? 'page' : undefined}
                  onClick={() => handleNavClick(item.key)}
                >
                  <span className="admin-nav-icon" aria-hidden="true">{item.icon}</span>
                  <span className="admin-nav-label">{item.label}</span>
                </button>
              </div>
            );
          })}
        </nav>

        <div className="sidefoot">
          <b>{userName}</b>
          <span>{me?.role === 'ADMIN' ? 'Administrator' : me?.role || 'Administrator'}</span>
          <br />
          {signOut && (
            <button type="button" className="linkbtn" onClick={signOut}>
              Sign Out
            </button>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="main admin-main-area">
        {/* Topbar (sticky header) */}
        <header className="topbar admin-topbar">
          {/* Mobile hamburger & brand (visible on mobile only) */}
          <div className="admin-mobile-bar">
            <button
              type="button"
              className="admin-mobile-toggle"
              aria-label="Open navigation menu"
              onClick={() => setMobileMenuOpen(true)}
            >
              ☰
            </button>
            <div className="admin-mobile-brand" onClick={() => handleNavClick('dashboard')} role="button" tabIndex={0}>
              <span className="mark">📚</span>
              <b>ADMIN</b>
            </div>
          </div>

          {/* Sync badge */}
          <span className="sync" title={syncNote}>
            <span className="dot"></span>
            <span className="sync-text">{syncNote}</span>
          </span>

          <span className="sp"></span>

          {/* API Base input (desktop only) */}
          {baseUrl !== undefined && setBaseUrl && (
            <input
              className="apibase"
              value={baseUrl}
              aria-label="API base URL"
              onChange={(e) => setBaseUrl(e.target.value)}
            />
          )}

          {/* User profile chip / dropdown */}
          <div className="admin-user-wrap" ref={dropdownRef}>
            <button
              type="button"
              className="userchip admin-user-btn"
              onClick={() => setUserDropdownOpen((v) => !v)}
              aria-label="Admin user menu"
            >
              <span className="avatar">{userInitials}</span>
              <span className="admin-user-name">{userName}</span>
            </button>

            {userDropdownOpen && (
              <div className="admin-user-menu" role="menu">
                <div className="admin-user-menu-head">
                  <b>{userName}</b>
                  <span>{me?.role === 'ADMIN' ? 'Administrator' : me?.role || 'Administrator'}</span>
                </div>
                <button
                  type="button"
                  className="admin-menu-item"
                  onClick={() => {
                    setUserDropdownOpen(false);
                    handleNavClick('settings');
                  }}
                >
                  ⚙️ Settings
                </button>
                {signOut && (
                  <button
                    type="button"
                    className="admin-menu-item danger"
                    onClick={() => {
                      setUserDropdownOpen(false);
                      signOut();
                    }}
                  >
                    🚪 Sign Out
                  </button>
                )}
              </div>
            )}
          </div>
        </header>

        {/* Page children */}
        <main className="content admin-content-wrap">
          {children}
        </main>
      </div>

      {/* Mobile Drawer Backdrop */}
      {mobileMenuOpen && (
        <div
          className="admin-mobile-backdrop"
          onClick={() => setMobileMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Mobile Slide-in Navigation Drawer */}
      <div
        className={`admin-mobile-drawer ${mobileMenuOpen ? 'open' : ''}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-label="Admin navigation drawer"
      >
        <div className="admin-mobile-drawer-header">
          <div className="brandrow">
            <span className="mark">📚</span>
            <div>
              <b>READER'S CIRCLE</b>
              <span>Admin Panel</span>
            </div>
          </div>
          <button
            type="button"
            className="admin-mobile-drawer-close"
            aria-label="Close navigation"
            onClick={() => setMobileMenuOpen(false)}
          >
            ✕
          </button>
        </div>

        <div className="admin-mobile-drawer-body">
          <nav className="admin-mobile-nav">
            {NAV_ITEMS.map((item, index) => {
              const prevItem = index > 0 ? NAV_ITEMS[index - 1] : null;
              const showGroup = item.group && (!prevItem || prevItem.group !== item.group);
              const isActive = activeView === item.key;
              return (
                <div key={item.key} className="admin-mobile-nav-item">
                  {showGroup && <div className="admin-mobile-nav-group">{item.group}</div>}
                  <button
                    type="button"
                    className={`admin-mobile-nav-btn ${isActive ? 'active' : ''}`.trim()}
                    onClick={() => handleNavClick(item.key)}
                  >
                    <span className="admin-nav-icon">{item.icon}</span>
                    <span className="admin-nav-label">{item.label}</span>
                  </button>
                </div>
              );
            })}
          </nav>
        </div>

        <div className="admin-mobile-drawer-footer">
          <div className="admin-drawer-user-info">
            <span className="avatar">{userInitials}</span>
            <div>
              <b>{userName}</b>
              <span>{me?.role === 'ADMIN' ? 'Administrator' : me?.role || 'Administrator'}</span>
            </div>
          </div>
          {signOut && (
            <button
              type="button"
              className="admin-mobile-signout"
              onClick={() => {
                setMobileMenuOpen(false);
                signOut();
              }}
            >
              🚪 Sign Out
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
