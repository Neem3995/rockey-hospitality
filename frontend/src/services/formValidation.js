/** @param {string} value @param {number} min @param {number} max */
export function textError(value, min, max) {
  return value.trim().length < min || value.length > max ? `Use ${min}–${max} characters.` : '';
}
/** Native email constraint plus a small whitespace check; backend validation remains authoritative.
 * @param {string} value
 */
export function emailError(value) {
  const input = document.createElement('input');
  input.type = 'email'; input.required = true; input.value = value;
  return !input.checkValidity() || value.length > 120 || /\s/.test(value) ? 'Enter a valid email of at most 120 characters.' : '';
}
/** @param {import('./apiClient.js').AuthState} auth */
export function authScopeKey(auth) {
  return JSON.stringify([auth.status, auth.sessionKey, auth.user?.id, auth.user?.role, auth.user?.employeeId, auth.user?.departmentSummary?.id]);
}
