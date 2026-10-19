import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api, clearTokens, getAccess, isDemotionAcked, isPromotionAcked, type Me } from '../lib/api';
import ReaderDashboard from './ReaderDashboard';

export default function Organizer() {
  const navigate = useNavigate();

  useEffect(() => {
    if (!getAccess()) {
      navigate('/auth');
      return;
    }
    // Role-transition notices live in ReaderLayout (embedded below via
    // ReaderDashboard). Here we only route: admins to the console, and
    // settled readers (already past the one-time demotion notice) to /reader.
    let on = true;
    async function guard() {
      try {
        const me = await api<Me>('/api/users/me');
        if (!on) return;
        if (me.role === 'ADMIN') {
          navigate('/admin');
          return;
        }
        if (me.role === 'READER' && (isDemotionAcked(me.id) || !isPromotionAcked(me.id))) {
          navigate('/reader');
        }
      } catch (e) {
        if (!on) return;
        if (e instanceof ApiError && e.status === 401) navigate('/auth');
      }
    }
    void guard();
    return () => {
      on = false;
    };
  }, [navigate]);

  function signOut() {
    clearTokens();
    navigate('/auth');
  }

  return (
    <>
      <div
        style={{
          background: '#6B2A1F',
          color: '#F7EFE0',
          padding: '10px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <b style={{ fontFamily: 'Georgia, serif', fontSize: 16 }}>👑 Organiser Mode</b>
        <span style={{ fontSize: 13, opacity: 0.9 }}>
          You have full reader access plus organiser tools to manage your reading circles and events directly from the Circles and Events tabs.
        </span>
        <button
          type="button"
          onClick={signOut}
          style={{
            background: 'transparent',
            color: '#F7EFE0',
            border: '1px solid #F7EFE0',
            borderRadius: 8,
            padding: '5px 14px',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          Sign out
        </button>
      </div>
      <ReaderDashboard />
    </>
  );
}
