'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { AuthState, AuthUser } from '@/lib/types';
import { authApi } from '@/lib/api';

interface AuthContextType extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  accessToken: null,
  isAuthenticated: false,
  login: async () => { },
  logout: () => { },
  loading: true,
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const userData = localStorage.getItem('user');
    if (token && userData) {
      setAccessToken(token);
      setUser(JSON.parse(userData));
    }
    setLoading(false);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password);
    const { accessToken: at, refreshToken: rt, user: u } = res.data.data;
    localStorage.setItem('accessToken', at);
    localStorage.setItem('refreshToken', rt);
    localStorage.setItem('user', JSON.stringify(u));
    setAccessToken(at);
    setUser(u);
  }, []);

  const logout = useCallback(() => {
    const rt = localStorage.getItem('refreshToken');
    if (rt) authApi.logout(rt).catch(() => { });
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    setAccessToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{
      user, accessToken, isAuthenticated: !!user, login, logout, loading,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
