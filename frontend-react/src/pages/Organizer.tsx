import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, api, getAccess, type Me } from '../lib/api';
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
        if (me.role === 'READER') {
          navigate('/reader', { replace: true });
          return;
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

  return <ReaderDashboard isOrganizer />;
}
