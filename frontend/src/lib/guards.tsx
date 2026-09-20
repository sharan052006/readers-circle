import { Navigate } from 'react-router-dom';
import type { JSX } from 'react';
import { useAuth, type Role } from './authContext';

export function RequireAuth({ children }: { children: JSX.Element }) {
  const { user, loading } = useAuth();
  if (loading) return <p className="rc-loading">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export function RequireRole({ roles, children }: { roles: Role[]; children: JSX.Element }) {
  const { user, loading } = useAuth();
  if (loading) return <p className="rc-loading">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}
