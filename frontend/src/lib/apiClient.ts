import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export type Role = "ADMIN" | "ORGANIZER" | "READER";

const ACCESS_KEY = "rc.accessToken";
const REFRESH_KEY = "rc.refreshToken";

/** Tokens live in sessionStorage (cleared with the tab) — never in code or logs. */
export function loadTokens(): TokenPair | null {
  const accessToken = sessionStorage.getItem(ACCESS_KEY);
  const refreshToken = sessionStorage.getItem(REFRESH_KEY);
  return accessToken && refreshToken ? { accessToken, refreshToken } : null;
}

export function saveTokens(pair: TokenPair): void {
  sessionStorage.setItem(ACCESS_KEY, pair.accessToken);
  sessionStorage.setItem(REFRESH_KEY, pair.refreshToken);
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}

/** Reads the role claim without a dependency — returns null for malformed tokens. */
export function decodeRole(token: string): Role | null {
  try {
    const segment = token.split(".")[1];
    if (!segment) return null;
    const role = (JSON.parse(atob(segment.replace(/-/g, "+").replace(/_/g, "/")))) as {
      role?: unknown;
    };
    return role.role === "ADMIN" || role.role === "ORGANIZER" || role.role === "READER"
      ? role.role
      : null;
  } catch {
    return null;
  }
}

/** Single-flight refresh: concurrent 401s share one POST, not N. */
let refreshFlight: Promise<TokenPair> | null = null;

function refreshTokens(raw: AxiosInstance): Promise<TokenPair> {
  if (!refreshFlight) {
    const current = loadTokens();
    refreshFlight = (async () => {
      if (!current) throw new Error("no session");
      const res = await raw.post<TokenPair>("/auth/refresh", {
        refreshToken: current.refreshToken,
      });
      saveTokens(res.data);
      return res.data;
    })().finally(() => {
      refreshFlight = null;
    });
  }
  return refreshFlight;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

export function createApi(
  baseURL: string = import.meta.env.VITE_API_URL ?? "/api",
): AxiosInstance {
  const instance = axios.create({ baseURL });

  instance.interceptors.request.use((config) => {
    const tokens = loadTokens();
    if (tokens) config.headers.set("Authorization", `Bearer ${tokens.accessToken}`);
    return config;
  });

  instance.interceptors.response.use(
    (res) => res,
    async (error: AxiosError) => {
      const original = error.config as RetriableConfig | undefined;
      if (
        error.response?.status !== 401 ||
        !original ||
        original._retry ||
        original.url?.endsWith("/auth/refresh")
      ) {
        throw error;
      }
      original._retry = true;
      try {
        const pair = await refreshTokens(instance);
        original.headers.set("Authorization", `Bearer ${pair.accessToken}`);
        return instance(original);
      } catch {
        clearTokens();
        throw error;
      }
    },
  );

  return instance;
}

/** Singleton used by pages — the single place tokens attach (spec code style). */
export const apiClient = createApi();
export const api = apiClient;
