const LS_API = 'rc_api_base';
const LS_A = 'rc_access';
const LS_R = 'rc_refresh';

const env = (typeof import.meta !== 'undefined' && import.meta.env) ? import.meta.env : undefined;

const ENV_API = (
  env?.VITE_API_BASE_URL ||
  env?.VITE_API_URL ||
  env?.VITE_BACKEND_URL ||
  ''
).trim().replace(/\/+$/, '');

const DEFAULT_PROD_API = 'https://readers-circle.onrender.com';
const DEFAULT_DEV_API = 'http://localhost:8080';

export function apiBase(): string {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_API) : null;
  if (stored) {
    const clean = stored.trim().replace(/\/+$/, '');
    const isRemote =
      typeof window !== 'undefined' &&
      window.location.hostname !== 'localhost' &&
      window.location.hostname !== '127.0.0.1';
    if (!isRemote || !clean.includes('localhost')) {
      return clean;
    }
  }
  if (ENV_API) {
    return ENV_API;
  }
  return env?.PROD ? DEFAULT_PROD_API : DEFAULT_DEV_API;
}

export function apiUrl(path: string): string {
  const base = apiBase().replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : '/' + path;
  return base + cleanPath;
}

export function setApiBase(v: string) {
  localStorage.setItem(LS_API, v.replace(/\/+$/, ''));
}

export function getAccess(): string | null {
  return localStorage.getItem(LS_A);
}

export function setTokens(access?: string | null, refresh?: string | null) {
  if (access) localStorage.setItem(LS_A, access);
  if (refresh) localStorage.setItem(LS_R, refresh);
}

export function clearTokens() {
  try {
    localStorage.removeItem(LS_A);
    localStorage.removeItem(LS_R);
  } catch {
    /* ignore storage errors */
  }
}

const LS_PROMO_ACK = 'rc_promotion_ack';
const LS_DEMO_ACK = 'rc_demotion_ack';

/** True once this user has seen the promotion congratulations (one-time notice). */
export function isPromotionAcked(userId?: string | null): boolean {
  return !!userId && safeGet(LS_PROMO_ACK) === userId;
}

/** True once this user has seen the demotion notice (one-time notice). */
export function isDemotionAcked(userId?: string | null): boolean {
  return !!userId && safeGet(LS_DEMO_ACK) === userId;
}

/** Fresh promotion = user's active session token is still a READER token, but backend has role ORGANIZER. */
export function isFreshPromotion(role?: string | null, userId?: string | null): boolean {
  if (role !== 'ORGANIZER') return false;
  const tokenRole = extractRoleFromToken(getAccess());
  // If current access token is already an ORGANIZER token, the user is already authenticated as organizer!
  if (tokenRole && tokenRole.toUpperCase() === 'ORGANIZER') {
    return false;
  }
  return !isPromotionAcked(userId);
}

/** Fresh demotion = user's active session token is still an ORGANIZER token, but backend has role READER. */
export function isFreshDemotion(role?: string | null, userId?: string | null): boolean {
  if (role !== 'READER') return false;
  const tokenRole = extractRoleFromToken(getAccess());
  // If current access token is already a READER token, the user is already authenticated as reader!
  if (tokenRole && tokenRole.toUpperCase() === 'READER') {
    return false;
  }
  return !isDemotionAcked(userId);
}

export function ackPromotion(userId: string) {
  safeSet(LS_PROMO_ACK, userId);
  safeDel(LS_DEMO_ACK);
}

export function ackDemotion(userId: string) {
  safeSet(LS_DEMO_ACK, userId);
  safeDel(LS_PROMO_ACK);
}

function safeGet(k: string): string | null {
  try {
    return localStorage.getItem(k);
  } catch {
    return null;
  }
}

function safeSet(k: string, v: string) {
  try {
    localStorage.setItem(k, v);
  } catch {
    /* notice may repeat, login still proceeds */
  }
}

function safeDel(k: string) {
  try {
    localStorage.removeItem(k);
  } catch {
    /* ignore */
  }
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export class NetworkTimeoutError extends Error {
  constructor(message = 'The server took too long to respond. Please try again.') {
    super(message);
    this.name = 'NetworkTimeoutError';
  }
}

export class NetworkConnectionError extends Error {
  constructor(message = 'Unable to reach the server. Please check your internet connection.') {
    super(message);
    this.name = 'NetworkConnectionError';
  }
}

export const DEFAULT_TIMEOUT_MS = 25000;

export async function fetchWithTimeout(
  input: RequestInfo | URL,
  init?: RequestInit,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);

  if (init?.signal) {
    init.signal.addEventListener('abort', () => controller.abort());
  }

  try {
    const res = await fetch(input, {
      ...init,
      signal: controller.signal,
    });
    return res;
  } catch (err: unknown) {
    if (err instanceof Error) {
      if (err.name === 'AbortError' || err.name === 'TimeoutError') {
        throw new NetworkTimeoutError(
          `Request timed out after ${Math.round(timeoutMs / 1000)}s. If the service was idle, the Render backend may still be waking up — please wait a moment and try again.`,
        );
      }
      if (err.name === 'TypeError' || /failed to fetch|networkerror|load failed/i.test(err.message)) {
        throw new NetworkConnectionError(
          `Cannot reach backend server (${apiBase()}). Please verify the service is running and accessible.`,
        );
      }
    }
    throw err;
  } finally {
    clearTimeout(id);
  }
}

type Opts = {
  method?: string;
  body?: string;
  headers?: Record<string, string>;
  raw?: boolean;
  timeoutMs?: number;
};

function getRefresh(): string | null {
  return localStorage.getItem(LS_R);
}

let refreshPromise: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const rt = getRefresh();
      if (!rt) return false;
      try {
        const r = await fetchWithTimeout(apiUrl('/api/auth/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: rt }),
        }, 15000);
        if (!r.ok) return false;
        const d = (await r.json().catch(() => ({}))) as Record<string, unknown>;
        const access = (d.accessToken ?? d.access_token) as string | undefined;
        const refresh = (d.refreshToken ?? d.refresh_token) as string | undefined;
        if (!access) return false;
        setTokens(access, refresh ?? undefined);
        return true;
      } catch {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

async function doFetch<T>(path: string, opts: Opts): Promise<T> {
  const headers: Record<string, string> = { ...(opts.headers || {}) };
  const t = getAccess();
  if (t) headers.Authorization = 'Bearer ' + t;
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const r = await fetchWithTimeout(
    apiUrl(path),
    { method: opts.method || 'GET', headers, body: opts.body },
    opts.timeoutMs || DEFAULT_TIMEOUT_MS,
  );
  if (r.status === 204) return null as T;
  let d: unknown = null;
  try {
    d = await r.json();
  } catch {
    d = null;
  }
  if (!r.ok) {
    const rec = d as Record<string, unknown> | null;
    let m = rec && (rec.message || rec.error);
    if (rec && rec.errors && typeof rec.errors === 'object') {
      const fieldErrors = Object.entries(rec.errors as Record<string, string>)
        .map(([k, v]) => `${k}: ${v}`)
        .join(', ');
      if (fieldErrors) {
        m = `Validation failed (${fieldErrors})`;
      }
    }
    const msg = Array.isArray(m) ? m.join(', ') : String(m || `Request failed: ${r.status}`);
    throw new ApiError(r.status, msg);
  }
  return d as T;
}

export async function api<T = unknown>(path: string, opts: Opts = {}): Promise<T> {
  try {
    return await doFetch<T>(path, opts);
  } catch (e) {
    // Access tokens live ~15 min: silently refresh once, then retry.
    // Never intercept the refresh call itself (avoids loops).
    if (e instanceof ApiError && e.status === 401 && !path.startsWith('/api/auth/')) {
      const ok = await tryRefresh();
      if (ok) return doFetch<T>(path, opts);
      clearTokens();
    }
    throw e;
  }
}

export type Me = {
  id?: string;
  name?: string;
  email?: string;
  role?: string;
  profilePictureUrl?: string;
  bio?: string;
  city?: string;
  interests?: string[];
};

/** Safely extracts user role from JWT token payload without network calls. */
export function extractRoleFromToken(token?: string | null): string | null {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    const parsed = JSON.parse(json) as { role?: string };
    return typeof parsed.role === 'string' ? parsed.role : null;
  } catch {
    return null;
  }
}

export async function loginApi(email: string, password: string, timeoutMs = 30000) {
  const r = await fetchWithTimeout(
    apiUrl('/api/auth/login'),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    },
    timeoutMs,
  );
  const d = (await r.json().catch(() => ({}))) as Record<string, unknown>;
  if (!r.ok) {
    let msg = (d.message as string) || (r.status === 401 ? 'Invalid email or password.' : `Sign in failed: ${r.status}`);
    if (r.status === 401 && msg.toLowerCase() === 'invalid credentials') {
      msg = 'Invalid email or password. Please verify your credentials.';
    }
    if (d.errors && typeof d.errors === 'object') {
      const errMap = d.errors as Record<string, string>;
      const details = Object.entries(errMap)
        .map(([k, v]) => `${k}: ${v}`)
        .join(', ');
      if (details) msg += ` (${details})`;
    }
    throw new ApiError(r.status, msg);
  }
  return d as { accessToken?: string; refreshToken?: string };
}

export async function registerApi(name: string, email: string, password: string, timeoutMs = 30000) {
  const r = await fetchWithTimeout(
    apiUrl('/api/auth/register'),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, email, password }),
    },
    timeoutMs,
  );
  const d = (await r.json().catch(() => ({}))) as Record<string, unknown>;
  if (!r.ok) {
    let msg = (d.message as string) || `Registration failed: ${r.status}`;
    if (r.status === 409) {
      msg = 'An account with this email address already exists.';
    }
    if (d.errors && typeof d.errors === 'object') {
      const errMap = d.errors as Record<string, string>;
      const details = Object.entries(errMap)
        .map(([k, v]) => `${k}: ${v}`)
        .join(', ');
      if (details) msg += ` (${details})`;
    }
    throw new ApiError(r.status, msg);
  }
  return d as { accessToken?: string; refreshToken?: string };
}

export function initialsOf(n?: string | null): string {
  const s = String(n || 'R').trim();
  return s ? s[0].toUpperCase() : 'R';
}

/** Resolve a stored media URL to a fetchable absolute URL.
 * Backend stores covers as relative paths like `/api/media/<file>`.
 * The React dev server runs on :5174, so a bare `/api/...` would hit Vite
 * (404) instead of the backend (:8080). Prefix with apiBase() in that case.
 */
export function resolveMedia(url?: string | null): string {
  if (!url) return '';
  const u = String(url).trim();
  if (!u) return '';
  if (/^https?:\/\//i.test(u) || u.startsWith('data:') || u.startsWith('blob:')) return u;
  if (u.startsWith('/')) return apiUrl(u);
  return u;
}

/** Upload an image (JPG/PNG/WEBP, max 5MB) → public /api/media URL. Used for event/circle covers. */
export async function uploadMedia(file: File): Promise<string> {
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (!['jpg', 'jpeg', 'png', 'webp'].includes(ext)) {
    throw new ApiError(400, 'Only JPG, PNG or WEBP images are allowed.');
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new ApiError(400, 'Image must be under 5MB.');
  }
  const fd = new FormData();
  fd.append('file', file);
  const headers: Record<string, string> = {};
  const t = getAccess();
  if (t) headers.Authorization = 'Bearer ' + t;
  const r = await fetchWithTimeout(apiUrl('/api/media'), { method: 'POST', headers, body: fd }, 30000);
  let d: unknown = null;
  try {
    d = await r.json();
  } catch {
    d = null;
  }
  if (!r.ok) {
    const rec = d as Record<string, unknown> | null;
    const m = rec && (rec.message || rec.error);
    throw new ApiError(r.status, String(m || `Upload failed: ${r.status}`));
  }
  const url = (d as Record<string, unknown> | null)?.url as string | undefined;
  if (!url) throw new ApiError(r.status, 'Upload failed: no URL returned.');
  return url;
}

export function timeAgo(iso?: string | null): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  const diff = Date.now() - t;
  if (isNaN(diff) || diff < 0) return '';
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h === 1 ? 'an hour ago' : h + ' hours ago';
  const d = Math.floor(h / 24);
  if (d === 1) return 'a day ago';
  if (d < 7) return d + ' days ago';
  const w = Math.floor(d / 7);
  if (w < 5) return w === 1 ? 'a week ago' : w + ' weeks ago';
  return new Date(iso).toLocaleDateString();
}

export function fmtDate(d?: string | null): string {
  if (!d) return '';
  const x = new Date(d.length <= 10 ? d + 'T00:00:00' : d);
  return isNaN(x.getTime()) ? String(d) : x.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export function fmtTime(t?: string | null): string {
  if (!t) return '';
  const p = String(t).split(':');
  let h = +p[0];
  const m = p[1] || '00';
  const ap = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  return h + ':' + m + ' ' + ap;
}
