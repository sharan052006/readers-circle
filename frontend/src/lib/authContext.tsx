import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, setTokens, getAccessToken } from './apiClient';

export type Role = 'ADMIN' | 'ORGANIZER' | 'READER';

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  role: Role;
}

interface AuthCtx {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  isAdmin: boolean;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchMe = useCallback(async () => {
    try {
      const res = await api.get('/users/me');
      setUser(res.data as AuthUser);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Access token lives only in memory: on reload, restore the session via
    // the httpOnly refresh cookie before fetching /me (spec: persist via refresh).
    (async () => {
      if (!getAccessToken()) {
        try {
          const res = await api.post('/auth/refresh', {});
          setTokens(res.data.accessToken, res.data.refreshToken);
        } catch {
          setLoading(false);
          return;
        }
      }
      await fetchMe();
    })();
  }, [fetchMe]);

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await api.post('/auth/login', { email, password });
      setTokens(res.data.accessToken, res.data.refreshToken);
      await fetchMe();
    },
    [fetchMe],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      const res = await api.post('/auth/register', { name, email, password });
      setTokens(res.data.accessToken, res.data.refreshToken);
      await fetchMe();
    },
    [fetchMe],
  );

  const logout = useCallback(() => {
    setTokens(null, null);
    setUser(null);
  }, []);

  return (
    <Ctx.Provider
      value={{ user, loading, login, register, logout, isAdmin: user?.role === 'ADMIN' }}
    >
      {children}
    </Ctx.Provider>
  );
}

export function useAuth(): AuthCtx {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside provider');
  return v;
}
