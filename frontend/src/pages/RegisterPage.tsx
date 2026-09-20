import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/authContext';

export default function RegisterPage() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await register(name, email, password);
      nav('/');
    } catch {
      setError('Registration failed (email may exist)');
    }
  }

  return (
    <main className="rc-page">
      <div className="rc-brand" aria-hidden="true">
        <span className="rc-brand-mark">R</span>Reader&apos;s Circle
      </div>
      <div className="rc-auth-wrap">
        <div className="rc-card">
          <h1>Register</h1>
          <p className="rc-card-sub">Join Reader&apos;s Circle to discover and discuss books.</p>
          <form onSubmit={onSubmit}>
            <label className="rc-field">
              Name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                maxLength={100}
                autoComplete="name"
                placeholder="Your display name"
              />
            </label>
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
              Password (min 8)
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                required
                minLength={8}
                autoComplete="new-password"
                placeholder="At least 8 characters"
              />
            </label>
            {error && (
              <p role="alert" className="rc-alert">
                {error}
              </p>
            )}
            <button type="submit" className="rc-btn">
              Create account
            </button>
          </form>
          <p className="rc-switch">
            Have an account? <Link to="/login">Login</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
