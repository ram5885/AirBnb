import { createContext, useContext, useEffect, useState } from 'react';
import * as api from '../api.js';

const STORAGE_KEY = 'airbnb_clone_user';
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) setUser(JSON.parse(stored));
    } catch {
      // ignore malformed/blocked storage
    } finally {
      setReady(true);
    }
  }, []);

  function persist(authResponse) {
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

  function logout() {
    setUser(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
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
