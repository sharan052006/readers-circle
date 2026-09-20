import { useEffect, useState } from 'react';
import { api } from '../lib/apiClient';
import type { AuthUser } from '../lib/authContext';

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AuthUser[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    setError('');
    try {
      const res = await api.get('/users');
      setUsers(res.data as AuthUser[]);
    } catch {
      setError('Failed to load users');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function setRole(id: string, role: string) {
    try {
      await api.patch(`/users/${id}`, { role });
      await load();
    } catch {
      setError('Role update failed');
    }
  }

  async function deactivate(id: string) {
    try {
      await api.delete(`/users/${id}`);
      await load();
    } catch {
      setError('Deactivation failed');
    }
  }

  return (
    <main className="rc-page">
      <div className="rc-page-head">
        <h1>Users</h1>
      </div>
      <p className="rc-page-sub">Platform members, roles, and account status.</p>
      {error && (
        <p role="alert" className="rc-alert">
          {error}
        </p>
      )}
      {loading ? (
        <p className="rc-loading" role="status">
          Loading users…
        </p>
      ) : users.length === 0 && !error ? (
        <p className="rc-empty">No users to display.</p>
      ) : (
        <ul className="rc-user-list">
          {users.map((u) => (
            <li key={u.id} className="rc-user-row">
              <span className="rc-user-id">
                {u.name} ({u.email}, {u.role})
              </span>
              <span
                className={
                  u.role === 'ADMIN'
                    ? 'rc-role rc-role-admin'
                    : u.role === 'ORGANIZER'
                      ? 'rc-role rc-role-organizer'
                      : 'rc-role'
                }
                aria-hidden="true"
              >
                {u.role}
              </span>
              <span className="rc-user-actions">
                <button
                  type="button"
                  className="rc-btn-secondary"
                  onClick={() => setRole(u.id, 'ORGANIZER')}
                >
                  Make organizer
                </button>
                <button
                  type="button"
                  className="rc-btn-danger-ghost"
                  onClick={() => deactivate(u.id)}
                >
                  Deactivate
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
