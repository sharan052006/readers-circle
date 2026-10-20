import { useRef, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ackDemotion,
  ackPromotion,
  api,
  apiBase,
  ApiError,
  extractRoleFromToken,
  loginApi,
  NetworkConnectionError,
  NetworkTimeoutError,
  registerApi,
  setTokens,
  type Me,
} from '../lib/api';
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
  const [statusHint, setStatusHint] = useState('');
  const hintTimerRef = useRef<number | null>(null);

  const isLogin = mode === 'login';

  function switchMode(m: Mode) {
    if (busy) return;
    setMode(m);
    setError('');
    setOk('');
    setStatusHint('');
  }

  function routeUser(role: string) {
    const normalizedRole = (role || 'READER').toUpperCase();
    if (redirectParam) {
      if (
        (redirectParam.startsWith('/admin') ||
          redirectParam.startsWith('/circles') ||
          redirectParam.startsWith('/events')) &&
        normalizedRole !== 'ADMIN'
      ) {
        navigate(normalizedRole === 'ORGANIZER' ? '/organizer' : '/reader', { replace: true });
        return;
      }
      navigate(redirectParam, { replace: true });
      return;
    }

    if (normalizedRole === 'ADMIN') {
      navigate('/admin', { replace: true });
    } else if (normalizedRole === 'ORGANIZER') {
      navigate('/organizer', { replace: true });
    } else {
      navigate('/reader', { replace: true });
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy) return; // Prevent duplicate submissions

    setError('');
    setOk('');
    setStatusHint('');
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

    // Inform the user if the backend takes longer than 4s (common on Render free-tier cold start)
    if (hintTimerRef.current) clearTimeout(hintTimerRef.current);
    hintTimerRef.current = window.setTimeout(() => {
      setStatusHint('Connecting to server… (if the service was idle, backend wake-up may take ~30s)');
    }, 4000);

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

      if (!access) {
        throw new Error('Authentication succeeded but no access token was returned.');
      }

      // Store tokens securely using existing project storage
      setTokens(access, refresh || null);

      // Extract user role directly from signed JWT token claims for instantaneous, resilient routing
      const tokenRole = extractRoleFromToken(access);

      // Attempt to load /me for personalized greeting, but don't block routing if it lags
      let me: Me | null = null;
      try {
        me = await api<Me>('/api/users/me', { timeoutMs: 5000 });
      } catch {
        // Fallback to token role and email
        if (tokenRole) {
          me = { role: tokenRole, email: em, name: nm || undefined };
        }
      }

      const activeRole = me?.role || tokenRole || 'READER';
      const welcomeName = me?.name || nm || me?.email || em;
      if (activeRole === 'ORGANIZER' && me?.id) {
        ackPromotion(me.id);
      } else if (activeRole === 'READER' && me?.id) {
        ackDemotion(me.id);
      }
      setOk(`Welcome ${welcomeName}! Taking you to your dashboard...`);
      setStatusHint('');

      setTimeout(() => {
        routeUser(activeRole);
      }, 250);
    } catch (ex: unknown) {
      if (ex instanceof NetworkTimeoutError) {
        setError(ex.message);
      } else if (ex instanceof NetworkConnectionError) {
        setError(ex.message);
      } else if (ex instanceof ApiError) {
        if (ex.status === 401) {
          const lowerMsg = ex.message.toLowerCase();
          if (lowerMsg.includes('two-factor')) {
            setError('Two-factor authentication code required.');
          } else {
            setError('Invalid email or password. Please verify your credentials and try again.');
          }
        } else if (ex.status === 409) {
          setError('An account with this email address already exists.');
        } else if (ex.status === 429) {
          setError('Too many attempts. Please wait a moment and try again.');
        } else {
          setError(ex.message || `Authentication failed (${ex.status}). Please try again.`);
        }
      } else {
        const msg = ex instanceof Error ? ex.message : String(ex);
        if (/failed to fetch|networkerror|load failed/i.test(msg)) {
          setError(
            `Cannot reach backend server (${apiBase()}). If the service was idle, the Render free-tier backend may take ~45s to wake up — please wait a moment and try again.`,
          );
        } else {
          setError(msg || 'An unexpected error occurred during sign in. Please try again.');
        }
      }
    } finally {
      if (hintTimerRef.current) {
        clearTimeout(hintTimerRef.current);
        hintTimerRef.current = null;
      }
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

              {busy && statusHint && (
                <div
                  className="auth-status-hint"
                  role="status"
                  aria-live="polite"
                  style={{
                    fontSize: 13,
                    color: '#6B6257',
                    marginTop: 10,
                    textAlign: 'center',
                    lineHeight: 1.4,
                  }}
                >
                  {statusHint}
                </div>
              )}
            </form>
          </section>
        </main>
      </div>
    </div>
  );
}
