import { API_BASE_URL } from './config.js';

/** @typedef {import('../routes/routeDefinitions.js').Role} Role */
/** @typedef {{id: number, name: string, email: string, role: Role, status: 'ACTIVE', employeeId: number | null, departmentSummary: {id: number, name: string} | null}} User */
/** @typedef {{status: 'checking' | 'authenticated' | 'anonymous' | 'recoverable-error', user: User | null, error: ApiError | null, sessionKey: number}} AuthState */
/** @typedef {{email: string, password: string, name?: string}} Credentials */

const messages = new Map([
  [0, 'Unable to reach the service. Check your connection and try again.'],
  [400, 'Check the form fields and try again.'],
  [401, 'Your session or sign-in details could not be verified.'],
  [403, 'You do not have permission to perform this action.'],
  [404, 'This account or resource is unavailable.'],
  [409, 'The request conflicts with the current account or session.'],
  [429, 'Too many attempts. Wait before trying again.'],
]);

export class ApiError extends Error {
  /** @param {number} status @param {Record<string, string>} [fieldErrors] */
  constructor(status, fieldErrors = {}) {
    super(messages.get(status) || 'The service is unavailable. Please try again.');
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export class StaleRequestError extends Error {
  constructor() { super('The session changed.'); this.name = 'StaleRequestError'; }
}

// The only access-token variable. Never exported, persisted, logged or put in Context.
let accessToken = '';
let expiresAt = 0;
let tokenVersion = 0;
let epoch = 0;
let identity = '';
let sessionKey = 0;
let lifetime = new AbortController();
/** @type {AuthState} */
let state = { status: 'checking', user: null, error: null, sessionKey };
/** @type {Set<() => void>} */
const listeners = new Set();
/** @type {{epoch: number, promise: Promise<void>} | null} */
let refreshPending = null;
/** @type {Promise<void> | null} */
let bootstrapPending = null;
/** @type {Promise<void> | null} */
let logoutPending = null;
let bootstrapped = false;

export function getAuthState() { return state; }
/** @param {() => void} listener */
export function subscribeAuth(listener) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
/** @param {AuthState['status']} status @param {User | null} user @param {ApiError | null} [error] */
function publish(status, user, error = null) {
  state = { status, user, error, sessionKey };
  for (const listener of listeners) listener();
}
/** @param {boolean} [invalidatePrivate] */
function newEpoch(invalidatePrivate = true) {
  lifetime.abort();
  lifetime = new AbortController();
  epoch += 1;
  if (invalidatePrivate) sessionKey += 1;
  return epoch;
}
/** @param {number} expected */
function checkEpoch(expected) {
  if (epoch !== expected) throw new StaleRequestError();
}
function clearSession() {
  newEpoch();
  accessToken = '';
  expiresAt = 0;
  identity = '';
  tokenVersion += 1;
  publish('anonymous', null);
}

/** Strip unknown fields (including any token fields) from the safe profile.
 * @param {unknown} value @returns {User}
 */
function safeUser(value) {
  const user = /** @type {Partial<User> | null} */ (value);
  if (!user || !Number.isSafeInteger(user.id) || Number(user.id) < 1
      || typeof user.name !== 'string' || typeof user.email !== 'string'
      || !['USER', 'STAFF', 'ADMIN'].includes(String(user.role)) || user.status !== 'ACTIVE'
      || (user.role !== 'USER' && (!Number.isSafeInteger(user.employeeId) || Number(user.employeeId) < 1))) {
    throw new ApiError(502);
  }
  const department = user.departmentSummary;
  return Object.freeze({
    id: /** @type {number} */ (user.id), name: user.name, email: user.email,
    role: /** @type {Role} */ (user.role), status: 'ACTIVE',
    employeeId: Number.isSafeInteger(user.employeeId) ? /** @type {number} */ (user.employeeId) : null,
    departmentSummary: department && Number.isSafeInteger(department.id) && typeof department.name === 'string'
      ? Object.freeze({ id: department.id, name: department.name }) : null,
  });
}
/** @param {User} user */
function userIdentity(user) {
  return JSON.stringify([user.id, user.role, user.status, user.employeeId, user.departmentSummary?.id]);
}

const managementConflicts = new Set([
  'Employees cannot be assigned to an inactive department.',
  'Employee email is already registered.',
  'Login email is already registered.',
  'Employee department changed concurrently. Retry the operation.',
  'Employee cannot be deactivated while active tasks are assigned.',
  'Department cannot be deactivated while active employees are assigned.',
  'Department cannot be deactivated while non-terminal tasks exist.',
  'Department cannot be deactivated while active inventory exists.',
  'Department name already exists.',
  'Request conflicts with existing data.',
]);

/** Only allowlisted field keys and fixed canonical conflict messages enter UI.
 * @param {Response} response @param {string} path
 */
async function responseError(response, path) {
  /** @type {Record<string, string>} */
  const fieldErrors = {};
  const error = new ApiError(response.status, fieldErrors);
  const management = /^\/(employees|departments)(\/\d+)?(\?|$)/.test(path);
  try {
    const data = await response.json();
    const keys = ['name', 'email', 'password'];
    if (management) keys.push('departmentId', 'jobRole', 'status', 'description', 'createLogin', 'loginEmail', 'temporaryPassword', 'securityRole', 'loginConfigurationValid');
    for (const key of keys) {
      if (data?.fieldErrors && typeof data.fieldErrors[key] === 'string') fieldErrors[key] = 'Check this value.';
    }
    if (management && response.status === 409) {
      error.message = managementConflicts.has(data?.message) ? data.message : 'Request conflicts with existing data.';
    }
  } catch { /* A non-JSON error still receives a safe status message. */ }
  return error;
}

/** @param {string} path @param {RequestInit} options @param {number} expected */
async function rawRequest(path, options, expected) {
  checkEpoch(expected);
  const signal = options.signal ? AbortSignal.any([lifetime.signal, options.signal]) : lifetime.signal;
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, { ...options, credentials: 'include', signal });
    checkEpoch(expected);
    if (!response.ok) throw await responseError(response, path);
    checkEpoch(expected);
    return response;
  } catch (error) {
    checkEpoch(expected);
    if (signal.aborted) throw new StaleRequestError();
    if (error instanceof ApiError) throw error;
    throw new ApiError(0);
  }
}
/** @param {Response} response @param {number} expected @returns {Promise<unknown>} */
async function readJson(response, expected) {
  try {
    const data = await response.json();
    checkEpoch(expected);
    return data;
  } catch (error) {
    checkEpoch(expected);
    if (error instanceof StaleRequestError) throw error;
    throw new ApiError(502);
  }
}

/** Auth response never leaves this module. Profile is revalidated without recursive refresh.
 * @param {Response} response @param {number} expected
 */
async function establishSession(response, expected) {
  const data = /** @type {{accessToken?: unknown, tokenType?: unknown, accessExpiresAt?: unknown}} */ (await readJson(response, expected));
  if (!data || typeof data.accessToken !== 'string' || !data.accessToken
      || data.tokenType !== 'Bearer' || typeof data.accessExpiresAt !== 'string'
      || !Number.isFinite(Date.parse(data.accessExpiresAt)) || Date.parse(data.accessExpiresAt) <= Date.now()) {
    throw new ApiError(502);
  }
  accessToken = data.accessToken;
  expiresAt = Date.parse(data.accessExpiresAt);
  tokenVersion += 1;
  const profileResponse = await rawRequest('/auth/me', { headers: { Authorization: `Bearer ${accessToken}` } }, expected);
  const user = safeUser(await readJson(profileResponse, expected));
  checkEpoch(expected);
  const nextIdentity = userIdentity(user);
  if (identity !== nextIdentity) sessionKey += 1;
  identity = nextIdentity;
  publish('authenticated', user);
}

/** @param {unknown} error @param {number} expected */
function sessionFailure(error, expected) {
  if (epoch !== expected || error instanceof StaleRequestError) return;
  const failure = error instanceof ApiError ? error : new ApiError(0);
  if ([400, 401, 403, 404, 409].includes(failure.status)) clearSession();
  else {
    sessionKey += 1;
    publish('recoverable-error', null, failure);
  }
}

/** One refresh including /me per epoch. No refresh body and no recursive retry.
 * @param {number} expected
 */
function refreshSession(expected) {
  if (refreshPending?.epoch === expected) return refreshPending.promise;
  const promise = (async () => {
    try {
      await establishSession(await rawRequest('/auth/refresh', { method: 'POST' }, expected), expected);
    } catch (error) {
      sessionFailure(error, expected);
      throw error;
    }
  })();
  refreshPending = { epoch: expected, promise };
  void promise.finally(() => {
    if (refreshPending?.promise === promise) refreshPending = null;
  }).catch(() => {});
  return promise;
}

/** StrictMode/remounts share the initial bootstrap, rather than rotating twice.
 * @param {boolean} [retry]
 */
export function bootstrapAuth(retry = false) {
  if (bootstrapPending) return bootstrapPending;
  if (bootstrapped && !retry) return Promise.resolve();
  bootstrapped = true;
  const expected = newEpoch();
  publish('checking', null);
  bootstrapPending = refreshSession(expected).catch(() => {}).finally(() => { bootstrapPending = null; });
  return bootstrapPending;
}

/** @param {'login' | 'register'} action @param {Credentials} credentials */
async function authenticate(action, credentials) {
  const expected = newEpoch();
  accessToken = '';
  expiresAt = 0;
  identity = '';
  publish('anonymous', null);
  // Explicit allowlist: registration cannot send roles, auth state or token fields.
  const body = action === 'register'
    ? { name: credentials.name, email: credentials.email, password: credentials.password }
    : { email: credentials.email, password: credentials.password };
  let accepted = false;
  try {
    const response = await rawRequest(`/auth/${action}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    }, expected);
    accepted = true;
    await establishSession(response, expected);
  } catch (error) {
    if (accepted) sessionFailure(error, expected);
    throw error;
  }
}
/** @param {Credentials} credentials */
export function login(credentials) { return authenticate('login', credentials); }
/** @param {Credentials & {name: string}} credentials */
export function register(credentials) { return authenticate('register', credentials); }

/** Protected future service calls; no caller can retrieve the token.
 * @param {string} path @param {RequestInit} [options] @returns {Promise<unknown>}
 */
export async function apiRequest(path, options = {}) {
  if (!path.startsWith('/') || path.startsWith('//') || /[\\#]/.test(path)
      || (path.startsWith('/auth/') && path !== '/auth/me')) throw new ApiError(400);
  if (state.status !== 'authenticated') throw new ApiError(401);
  const expected = epoch;
  const expectedIdentity = identity;
  const expectedSessionKey = sessionKey;
  const version = tokenVersion;
  const checkScope = () => {
    checkEpoch(expected);
    if (identity !== expectedIdentity || sessionKey !== expectedSessionKey || state.status !== 'authenticated') throw new StaleRequestError();
  };
  const send = () => {
    checkScope();
    const headers = new Headers(options.headers);
    headers.set('Authorization', `Bearer ${accessToken}`);
    return rawRequest(path, { ...options, headers }, expected);
  };
  let response;
  try { response = await send(); }
  catch (error) {
    checkScope();
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    if (version === tokenVersion) await refreshSession(expected);
    checkScope();
    try { response = await send(); }
    catch (retryError) {
      checkScope();
      if (retryError instanceof ApiError && retryError.status === 401) clearSession();
      throw retryError;
    }
  }
  const result = response.status === 204 ? null : await readJson(response, expected);
  checkScope();
  return result;
}

/** Confirm revocation only on 204; failure never reports a successful logout. */
export function logout() {
  if (logoutPending) return logoutPending;
  const expected = newEpoch(false);
  const expectedUserId = state.user?.id;
  publish(state.status, state.user, state.error);
  const promise = (async () => {
    if (!accessToken || expiresAt <= Date.now() || state.status !== 'authenticated') await refreshSession(expected);
    checkEpoch(expected);
    if (expectedUserId && state.user?.id !== expectedUserId) throw new StaleRequestError();
    const send = () => rawRequest('/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${accessToken}` } }, expected);
    let response;
    try { response = await send(); }
    catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      await refreshSession(expected);
      checkEpoch(expected);
      if (expectedUserId && state.user?.id !== expectedUserId) throw new StaleRequestError();
      response = await send();
    }
    if (response.status !== 204) throw new ApiError(502);
    checkEpoch(expected);
    clearSession();
  })();
  logoutPending = promise;
  void promise.finally(() => { if (logoutPending === promise) logoutPending = null; }).catch(() => {});
  return promise;
}
