import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import * as api from '../api.js';

const STORAGE_KEY = 'airbnb_clone_user';

export function isHotelManager(user) {
  return !!user?.roles?.includes('HOTEL_MANAGER');
}

/** Landing page for a signed-in user: hotel managers get their dashboard, everyone else the home page. */
export function homePathFor(user) {
  return isHotelManager(user) ? '/manager' : '/';
}
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    api.configureAuth({ token: null });
    setUser(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    // A 401 from a protected API means the token expired or was rejected: sign out.
    api.configureAuth({ token: null, handleUnauthorized: logout });
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        api.configureAuth({ token: parsed.token });
        setUser(parsed);
      }
    } catch {
      // ignore malformed/blocked storage
    } finally {
      setReady(true);
    }
  }, [logout]);

  function persist(authResponse) {
    api.configureAuth({ token: authResponse.token });
    setUser(authResponse);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(authResponse));
    } catch {
      // storage unavailable (private mode, quota) - session still works in-memory
    }
  }

  async function login(credentials) {
    const res = await api.login(credentials);
    persist(res);
    return res;
  }

  async function signup(details) {
    const res = await api.signup(details);
    persist(res);
    return res;
  }

  return (
    <AuthContext.Provider value={{ user, ready, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
