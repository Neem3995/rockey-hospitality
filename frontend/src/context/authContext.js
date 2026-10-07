import { createContext } from 'react';
import { bootstrapAuth, login, register, logout } from '../services/apiClient.js';

/** @typedef {import('../services/apiClient.js').AuthState & {login: typeof login, register: typeof register, logout: typeof logout, retry: () => Promise<void>}} AuthContextValue */
/** @type {import('react').Context<AuthContextValue | null>} */
export const AuthContext = createContext(/** @type {AuthContextValue | null} */ (null));
export const authActions = { login, register, logout, retry: () => bootstrapAuth(true) };
