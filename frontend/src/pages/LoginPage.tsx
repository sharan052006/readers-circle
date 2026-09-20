import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/authContext';

export default function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      nav('/');
    } catch {
      setError('Invalid credentials');
    }
  }

  return (
    <main className="rc-page">
      <div className="rc-brand" aria-hidden="true">
        <span className="rc-brand-mark">R</span>Reader&apos;s Circle
      </div>
      <div className="rc-auth-wrap">
        <div className="rc-card">
          <h1>Login</h1>
          <p className="rc-card-sub">Welcome back to your reading circle.</p>
          <form onSubmit={onSubmit}>
            <label className="rc-field">
              Email
              <input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                required
                autoComplete="email"
                placeholder="you@example.com"
              />
            </label>
            <label className="rc-field">
              Password
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                required
                autoComplete="current-password"
                placeholder="Your password"
              />
            </label>
            {error && (
              <p role="alert" className="rc-alert">
                {error}
              </p>
            )}
            <button type="submit" className="rc-btn">
              Login
            </button>
          </form>
          <p className="rc-switch">
            No account? <Link to="/register">Register</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
