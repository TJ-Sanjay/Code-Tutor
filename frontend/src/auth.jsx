import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api, { TOKEN_KEY, get } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(!!localStorage.getItem(TOKEN_KEY));

  const signOut = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    try {
      setUser(await get('/auth/me'));
    } catch {
      /* ignore – the 401 handler signs out */
    }
  }, []);

  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return;
    get('/auth/me')
      .then(setUser)
      .catch(() => localStorage.removeItem(TOKEN_KEY))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const onExpired = () => signOut();
    window.addEventListener('auth:expired', onExpired);
    return () => window.removeEventListener('auth:expired', onExpired);
  }, [signOut]);

  const accept = (data) => {
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    setUser({
      id: data.userId,
      username: data.username,
      email: data.email,
      fullName: data.fullName,
      role: data.role,
      storageUsedBytes: data.storageUsedBytes,
      storageLimitBytes: data.storageLimitBytes,
    });
  };

  const login = useCallback(async (identifier, password) => {
    const res = await api.post('/auth/login', { identifier, password });
    accept(res.data.data);
  }, []);

  const register = useCallback(async (form) => {
    const res = await api.post('/auth/register', form);
    accept(res.data.data);
  }, []);

  const value = useMemo(() => ({ user, loading, login, register, logout: signOut, refresh }), [user, loading, login, register, signOut, refresh]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
