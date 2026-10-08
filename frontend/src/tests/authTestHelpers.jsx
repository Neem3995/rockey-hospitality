import { AuthContext, authActions } from '../context/authContext.js';

/** Synthetic identity only; no credentials or tokens in reusable fixtures.
 * @param {import('../routes/routeDefinitions.js').Role} [role]
 * @returns {import('../services/apiClient.js').User}
 */
export function testUser(role = 'USER') {
  return { id: 1, name: 'Synthetic reviewer', email: 'reviewer@example.test', role, active: true };
}

/** @param {{children: import('react').ReactNode, value?: Partial<import('../context/authContext.js').AuthContextValue>}} props */
export function TestAuth({ children, value = {} }) {
  return <AuthContext.Provider value={{ status: 'anonymous', user: null, error: null, sessionKey: 0, ...authActions, ...value }}>{children}</AuthContext.Provider>;
}
