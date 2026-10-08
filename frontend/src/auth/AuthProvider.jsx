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
