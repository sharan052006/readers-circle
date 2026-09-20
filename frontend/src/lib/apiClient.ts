import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

let accessToken: string | null = null;
let refreshToken: string | null = null;

export function setTokens(access: string | null, refresh: string | null) {
  accessToken = access;
  refreshToken = refresh;
}

export function getAccessToken() {
  return accessToken;
}

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

async function refreshSession(): Promise<void> {
  const res = await axios.post(
    '/api/auth/refresh',
    refreshToken ? { refreshToken } : {},
    { withCredentials: true },
  );
  const data = res.data as { accessToken: string; refreshToken: string };
  setTokens(data.accessToken, data.refreshToken);
}

api.interceptors.response.use(
  (r) => r,
  async (err: AxiosError) => {
    const config = err.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (err.response?.status === 401 && config && !config._retry) {
      config._retry = true;
      try {
        await refreshSession();
        if (accessToken) {
          config.headers.set('Authorization', `Bearer ${accessToken}`);
        }
        return api(config);
      } catch {
        setTokens(null, null);
      }
    }
    throw err;
  },
);
