/*
 * STUDY NOTE: This module defines the shared Context and actions used by AuthProvider and useAuth.
 * createContext supplies a place for the provider's snapshot; null means no provider is present.
 * login/register/logout/retry delegate to apiClient. Importing the module does not sign anyone in.
 * Consumers receive a safe profile and actions, never the private access-token variable.
 */
import { createContext } from 'react';
import { bootstrapAuth, login, register, logout } from '../services/apiClient.js';

/** @typedef {import('../services/apiClient.js').AuthState & {login: typeof login, register: typeof register, logout: typeof logout, retry: () => Promise<void>}} AuthContextValue */
/** @type {import('react').Context<AuthContextValue | null>} */
export const AuthContext = createContext(/** @type {AuthContextValue | null} */ (null));
export const authActions = { login, register, logout, retry: () => bootstrapAuth(true) };
