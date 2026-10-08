/*
 * STUDY NOTE: Pages, layout and guards call this hook during rendering to read AuthContext.
 * useContext returns the nearest AuthProvider value and lets React update consumers when it changes.
 * We fail clearly if the provider is missing. This hook does not fetch, persist tokens or grant roles.
 */
import { useContext } from 'react';
import { AuthContext } from './authContext.js';

export default function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('Authentication provider is required.');
  return auth;
}
