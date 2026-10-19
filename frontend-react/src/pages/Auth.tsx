import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, loginApi, registerApi, setTokens, type Me } from '../lib/api';
import './Auth.css';

type Mode = 'login' | 'register';

export default function Auth() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirectParam = params.get('redirect');

  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);

  const isLogin = mode === 'login';

  function switchMode(m: Mode) {
    setMode(m);
    setError('');
    setOk('');
  }

  function routeUser(me: Me) {
    if (redirectParam) {
      if (
        (redirectParam.startsWith('/admin') || redirectParam.startsWith('/circles') || redirectParam.startsWith('/events')) &&
        me.role !== 'ADMIN'
      ) {
        navigate(me.role === 'ORGANIZER' ? '/organizer' : '/reader');
        return;
      }
      navigate(redirectParam);
      return;
    }

    if (me.role === 'ADMIN') {
      navigate('/admin');
    } else if (me.role === 'ORGANIZER') {
      navigate('/organizer');
    } else {
      navigate('/reader');
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setOk('');
    const em = email.trim();
    const nm = name.trim();

    if (!em || !password) {
      setError('Please enter your email and password.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (mode === 'register' && !nm) {
      setError('Please enter your name.');
      return;
    }

    setBusy(true);
    try {
      const data = mode === 'login' ? await loginApi(em, password) : await registerApi(nm, em, password);
      const raw = data as Record<string, unknown>;
      const access =
        (typeof raw.accessToken === 'string' && raw.accessToken) ||
        (typeof raw.access_token === 'string' && raw.access_token) ||
        (typeof raw.token === 'string' && raw.token) ||
        '';
      const refresh =
        (typeof raw.refreshToken === 'string' && raw.refreshToken) ||
        (typeof raw.refresh_token === 'string' && raw.refresh_token) ||
        '';
      if (access) setTokens(access, refresh || null);

      const me = await api<Me>('/api/users/me');
      setOk('Welcome ' + (me.name || me.email) + '! Taking you to your dashboard...');
      setTimeout(() => {
        routeUser(me);
      }, 300);
    } catch (ex) {
      const msg = ex instanceof Error ? ex.message : String(ex);
      setError(
        msg === 'Failed to fetch'
          ? 'Cannot reach backend server. Please verify backend is running on :8080.'
          : msg,
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-root">
      <div className="shell">
        <aside className="brand" aria-label="About Reader's Circle">
          <div>
            <div className="logo">
              <span className="mark">R</span> Reader&rsquo;s Circle
            </div>
            <h1>Where readers become a circle.</h1>
            <p className="sub">
              Join reading circles, reserve seats at events, and keep literary discussions alive.
            </p>
            <div className="quote-card">
              <blockquote>
                &ldquo;A reader lives a thousand lives before he dies. The man who never reads lives only one.&rdquo;
              </blockquote>
              <cite>— George R.R. Martin</cite>
            </div>
          </div>
        </aside>

        <main className="panel">
          <section className="card" aria-labelledby="title">
            <h2 id="title">{isLogin ? 'Sign In' : 'Create Account'}</h2>
            <p className="lead" id="subtitle">
              {isLogin
                ? 'Enter your credentials to access your dashboard.'
                : 'Create an account to join reading circles and events.'}
            </p>

            <div className="tabs" role="tablist" aria-label="Authentication mode">
              <button type="button" role="tab" aria-selected={isLogin} onClick={() => switchMode('login')}>
                Sign in
              </button>
              <button type="button" role="tab" aria-selected={!isLogin} onClick={() => switchMode('register')}>
                Create account
              </button>
            </div>

            <form onSubmit={onSubmit} noValidate>
              {!isLogin && (
                <div>
                  <label htmlFor="name">Full name</label>
                  <input
                    id="name"
                    type="text"
                    placeholder="e.g. Asha Sharma"
                    autoComplete="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </div>
              )}

              <label htmlFor="email">Email or User ID</label>
              <input
                id="email"
                type="email"
                placeholder="you@example.com"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />

              <div>
                <label htmlFor="password">Password</label>
                <div className="pw-wrap">
                  <input
                    id="password"
                    type={showPw ? 'text' : 'password'}
                    placeholder="Minimum 8 characters"
                    autoComplete={isLogin ? 'current-password' : 'new-password'}
                    required
                    minLength={8}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <button
                    className="pw-toggle"
                    type="button"
                    aria-label={showPw ? 'Hide password' : 'Show password'}
                    onClick={() => setShowPw((s) => !s)}
                  >
                    {showPw ? 'Hide' : 'Show'}
                  </button>
                </div>
              </div>

              {error && (
                <div className="error" role="alert" style={{ display: 'block' }}>
                  {error}
                </div>
              )}

              {ok && (
                <div className="success" role="status" style={{ display: 'block' }}>
                  {ok}
                </div>
              )}

              <button className="primary" type="submit" disabled={busy} style={{ marginTop: 16 }}>
                {busy ? 'Please wait...' : isLogin ? 'Sign In →' : 'Create Account →'}
              </button>
            </form>
          </section>
        </main>
      </div>
    </div>
  );
}
