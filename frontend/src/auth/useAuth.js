import { useContext } from 'react';
import { AuthContext } from './authContext.js';

export default function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('Authentication provider is required.');
  return auth;
}
