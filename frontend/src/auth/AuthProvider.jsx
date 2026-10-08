/*
 * STUDY NOTE: main.jsx wraps App here so descendants can read a safe shared auth snapshot.
 * On mount we subscribe to apiClient, request session bootstrap and publish updates through Context.
 * The effect unsubscribes on cleanup. Its empty dependency array means this subscription is not
 * re-created for each render; apiClient coalesces bootstrap calls. No token is stored in Context.
 */
import { useEffect, useState } from 'react';
import { bootstrapAuth, getAuthState, subscribeAuth } from '../services/apiClient.js';
import { AuthContext, authActions } from './authContext.js';

/** @param {{children: import('react').ReactNode}} props */
export default function AuthProvider({ children }) {
  const [auth, setAuth] = useState(getAuthState);
  useEffect(() => {
    const unsubscribe = subscribeAuth(() => setAuth(getAuthState()));
    setAuth(getAuthState());
    void bootstrapAuth();
    return unsubscribe;
  }, []);
  return <AuthContext.Provider value={{ ...auth, ...authActions }}>{children}</AuthContext.Provider>;
}
