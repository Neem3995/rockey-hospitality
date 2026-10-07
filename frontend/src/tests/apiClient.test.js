import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { testUser } from './authTestHelpers.jsx';

/** @type {typeof import('../services/apiClient.js')} */
let client;
let fetchMock = vi.fn();
/** @param {unknown} [data] @param {number} [status] */
const reply = (data = {}, status = 200) => new Response(status === 204 ? null : JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
/** Runtime-only synthetic opaque tokens, never copied to files/logs/storage.
 * @param {number} [expiresIn]
 */
const session = (expiresIn = 60000) => ({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', accessExpiresAt: new Date(Date.now() + expiresIn).toISOString() });
const credentials = () => ({ email: `${crypto.randomUUID()}@example.test`, password: crypto.randomUUID() });
function deferred() {
  /** @type {(response: Response) => void} */
  let resolve = () => {};
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(async () => {
  vi.resetModules();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  client = await import('../services/apiClient.js');
});
afterEach(() => { vi.unstubAllGlobals(); });

/** @param {import('../services/apiClient.js').User} [user] */
async function authenticated(user = testUser()) {
  fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(user));
  await client.bootstrapAuth();
}

describe('FE-02 API client', () => {
  it('shares initial bootstrap, sends only cookie credentials and initializes the safe /me profile', async () => {
    const token = session();
    const user = testUser('STAFF');
    fetchMock.mockResolvedValueOnce(reply(token)).mockResolvedValueOnce(reply({ ...user, accessToken: token.accessToken, password: crypto.randomUUID() }));
    const store = vi.spyOn(Storage.prototype, 'setItem');
    const read = vi.spyOn(Storage.prototype, 'getItem');
    const cookie = vi.spyOn(document, 'cookie', 'get');
    await Promise.all([client.bootstrapAuth(), client.bootstrapAuth()]);
    await client.bootstrapAuth();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/auth/refresh');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST', credentials: 'include' });
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    expect(fetchMock.mock.calls[1][0]).toBe('http://localhost:8080/api/auth/me');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe(`Bearer ${token.accessToken}`);
    expect(client.getAuthState().user).toEqual(user);
    expect(JSON.stringify(client.getAuthState())).not.toContain(token.accessToken);
    expect(store).not.toHaveBeenCalled();
    expect(read).not.toHaveBeenCalled();
    expect(cookie).not.toHaveBeenCalled();
  });

  it.each([400, 401, 403, 404, 409])('terminal bootstrap %s becomes anonymous without recursive refresh', async (status) => {
    fetchMock.mockResolvedValue(reply({}, status));
    await client.bootstrapAuth();
    expect(client.getAuthState().status).toBe('anonymous');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it.each([429, 500, 502])('bootstrap %s is recoverable and explicit retry revalidates identity', async (status) => {
    fetchMock.mockResolvedValueOnce(reply({}, status));
    await client.bootstrapAuth();
    expect(client.getAuthState()).toMatchObject({ status: 'recoverable-error', user: null });
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser()));
    await client.bootstrapAuth(true);
    expect(client.getAuthState().status).toBe('authenticated');
  });

  it('treats a network bootstrap failure as recoverable, without pretending revocation', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Synthetic offline'));
    await client.bootstrapAuth();
    expect(client.getAuthState().status).toBe('recoverable-error');
    expect(client.getAuthState().error?.status).toBe(0);
  });

  it.each(['login', 'register'])('%s uses an allowlisted JSON body and then /me; does not return tokens', async (action) => {
    const input = { ...credentials(), name: 'Synthetic reviewer', role: 'ADMIN', refreshToken: crypto.randomUUID() };
    fetchMock.mockResolvedValueOnce(reply(session(), action === 'register' ? 201 : 200)).mockResolvedValueOnce(reply(testUser()));
    const result = action === 'register' ? await client.register(input) : await client.login(input);
    expect(result).toBeUndefined();
    const request = fetchMock.mock.calls[0][1];
    expect(request.credentials).toBe('include');
    expect(JSON.parse(request.body)).toEqual(action === 'register' ? { name: input.name, email: input.email, password: input.password } : { email: input.email, password: input.password });
    expect(client.getAuthState().user?.role).toBe('USER');
  });

  it.each(['login', 'register'])('failed %s does not refresh or echo sensitive server text', async (action) => {
    const secret = crypto.randomUUID();
    fetchMock.mockResolvedValueOnce(reply({ message: secret, fieldErrors: { password: secret, role: secret } }, 401));
    const result = action === 'login' ? client.login(credentials()) : client.register({ ...credentials(), name: 'Synthetic reviewer' });
    await expect(result).rejects.toMatchObject({ status: 401, fieldErrors: { password: 'Check this value.' } });
    expect(client.getAuthState().status).toBe('anonymous');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('a /me 401 after successful login clears auth without recursive refresh', async () => {
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply({}, 401));
    await expect(client.login(credentials())).rejects.toMatchObject({ status: 401 });
    expect(client.getAuthState().status).toBe('anonymous');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('shares one refresh across concurrent protected 401s and retries with the new token', async () => {
    await authenticated();
    const refreshed = session();
    const pending = deferred();
    fetchMock.mockImplementation((url, options) => {
      if (url.endsWith('/refresh')) return pending.promise;
      const bearer = new Headers(options.headers).get('Authorization');
      return Promise.resolve(reply(testUser(), bearer === `Bearer ${refreshed.accessToken}` ? 200 : 401));
    });
    const requests = [client.apiRequest('/auth/me'), client.apiRequest('/auth/me')];
    await vi.waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(2)); // Initial bootstrap + single refresh.
    pending.resolve(reply(refreshed));
    expect(await Promise.all(requests)).toEqual([testUser(), testUser()]);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(2);
  });

  it('late 401 from an old token reuses the already refreshed token', async () => {
    await authenticated();
    const late = deferred();
    const refreshed = session();
    fetchMock.mockReturnValueOnce(late.promise).mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(refreshed)).mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply(testUser()));
    const oldRequest = client.apiRequest('/auth/me');
    await client.apiRequest('/auth/me');
    fetchMock.mockResolvedValueOnce(reply(testUser()));
    late.resolve(reply({}, 401));
    await oldRequest;
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/refresh'))).toHaveLength(2);
  });

  it('retries each protected request once only; a second 401 clears private state', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply({}, 401));
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 401 });
    expect(client.getAuthState()).toMatchObject({ status: 'anonymous', user: null });
    expect(fetchMock).toHaveBeenCalledTimes(6);
  });

  it.each([401, 409, 429, 500])('protected refresh %s applies terminal versus recoverable state', async (status) => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply({}, status));
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status });
    expect(client.getAuthState().status).toBe([401, 409].includes(status) ? 'anonymous' : 'recoverable-error');
    expect(client.getAuthState().user).toBeNull();
  });

  it('ordinary 403 never refreshes', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 403));
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 403 });
    expect(client.getAuthState().status).toBe('authenticated');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('protected refresh network failure is recoverable and discards other private responses', async () => {
    await authenticated();
    const late = deferred();
    fetchMock.mockReturnValueOnce(late.promise).mockResolvedValueOnce(reply({}, 401)).mockRejectedValueOnce(new TypeError('Offline'));
    const request = client.apiRequest('/auth/me').catch((error) => error);
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 0 });
    expect(client.getAuthState().status).toBe('recoverable-error');
    late.resolve(reply(testUser()));
    expect(await request).toBeInstanceOf(client.StaleRequestError);
  });

  it('logout refuses to revoke a different identity obtained during refresh', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply({ ...testUser(), id: 4 }));
    await expect(client.logout()).rejects.toBeInstanceOf(client.StaleRequestError);
    expect(client.getAuthState().user?.id).toBe(4);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/logout'))).toHaveLength(1);
  });

  it('second logout 401 does not recurse or claim server revocation', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply({}, 401));
    await expect(client.logout()).rejects.toMatchObject({ status: 401 });
    expect(fetchMock.mock.calls.slice(2).map(([url]) => url.split('/').pop())).toEqual(['logout', 'refresh', 'me', 'logout']);
    expect(client.getAuthState().status).toBe('authenticated');
  });

  it('only confirms logout on 204 and does not parse its empty body', async () => {
    await authenticated();
    const response = reply(null, 204);
    const json = vi.spyOn(response, 'json');
    fetchMock.mockResolvedValueOnce(response);
    await Promise.all([client.logout(), client.logout()]);
    expect(client.getAuthState()).toMatchObject({ status: 'anonymous', user: null });
    expect(json).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('logout obtains a fresh access token when expiry is reached', async () => {
    const initial = session(1000);
    fetchMock.mockResolvedValueOnce(reply(initial)).mockResolvedValueOnce(reply(testUser()));
    await client.bootstrapAuth();
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse(initial.accessExpiresAt) + 1);
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply(null, 204));
    await client.logout();
    expect(fetchMock.mock.calls.slice(2).map(([url]) => url.split('/').pop())).toEqual(['refresh', 'me', 'logout']);
  });

  it('logout 401 refreshes once, then confirms only after 204', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply(null, 204));
    await client.logout();
    expect(client.getAuthState().status).toBe('anonymous');
    expect(fetchMock.mock.calls.slice(2).map(([url]) => url.split('/').pop())).toEqual(['logout', 'refresh', 'me', 'logout']);
  });

  it.each([200, 401, 500])('failed logout %s does not falsely confirm revocation', async (status) => {
    await authenticated();
    fetchMock.mockResolvedValue(reply({}, status));
    if (status === 401) {
      // A refresh terminal rejection legitimately clears local auth, but logout still rejects.
      await expect(client.logout()).rejects.toMatchObject({ status: 401 });
    } else {
      await expect(client.logout()).rejects.toMatchObject({ status: status === 200 ? 502 : 500 });
      expect(client.getAuthState().status).toBe('authenticated');
    }
  });

  it('logout network failure preserves verified identity and can be retried', async () => {
    await authenticated();
    fetchMock.mockRejectedValueOnce(new TypeError('Offline'));
    await expect(client.logout()).rejects.toMatchObject({ status: 0 });
    expect(client.getAuthState().status).toBe('authenticated');
    fetchMock.mockResolvedValueOnce(reply(null, 204));
    await client.logout();
    expect(client.getAuthState().status).toBe('anonymous');
  });

  it('a stale bootstrap cannot overwrite a newer login', async () => {
    const stale = deferred();
    fetchMock.mockReturnValueOnce(stale.promise);
    const bootstrap = client.bootstrapAuth();
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser('ADMIN')));
    await client.login(credentials());
    stale.resolve(reply(session()));
    await bootstrap;
    expect(client.getAuthState().user?.role).toBe('ADMIN');
  });

  it('a stale /me body cannot overwrite a newer identity', async () => {
    const stale = deferred();
    fetchMock.mockResolvedValueOnce(reply(session())).mockReturnValueOnce(stale.promise);
    const previous = client.login(credentials()).catch((error) => error);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply({ ...testUser('STAFF'), id: 3 }));
    await client.login(credentials());
    stale.resolve(reply(testUser()));
    expect(await previous).toBeInstanceOf(client.StaleRequestError);
    expect(client.getAuthState().user?.id).toBe(3);
  });

  it('authorization change invalidates old requests and increments private view key', async () => {
    await authenticated();
    const oldKey = client.getAuthState().sessionKey;
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser('STAFF')));
    await expect(client.apiRequest('/auth/me')).rejects.toBeInstanceOf(client.StaleRequestError);
    expect(client.getAuthState().user?.role).toBe('STAFF');
    expect(client.getAuthState().sessionKey).toBeGreaterThan(oldKey);
  });

  it('stale protected success is discarded after logout', async () => {
    await authenticated();
    const pending = deferred();
    fetchMock.mockReturnValueOnce(pending.promise).mockResolvedValueOnce(reply(null, 204));
    const request = client.apiRequest('/auth/me').catch((error) => error);
    await client.logout();
    pending.resolve(reply(testUser()));
    expect(await request).toBeInstanceOf(client.StaleRequestError);
  });

  it('old logout cannot clear a newer login', async () => {
    await authenticated();
    const pending = deferred();
    fetchMock.mockReturnValueOnce(pending.promise);
    const signout = client.logout().catch((error) => error);
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply({ ...testUser(), id: 4 }));
    await client.login(credentials());
    pending.resolve(reply(null, 204));
    expect(await signout).toBeInstanceOf(client.StaleRequestError);
    expect(client.getAuthState().user?.id).toBe(4);
  });

  it('rejects malformed /me and token responses without exposing unknown data', async () => {
    fetchMock.mockResolvedValueOnce(reply({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', accessExpiresAt: 'invalid' }));
    await client.bootstrapAuth();
    expect(client.getAuthState().status).toBe('recoverable-error');
    fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply({ ...testUser(), role: 'MANAGER' }));
    await client.bootstrapAuth(true);
    expect(client.getAuthState().user).toBeNull();
  });

  it('rejects arbitrary origins/auth paths and does not leak a token through caller headers', async () => {
    await authenticated();
    for (const path of ['https://example.test', '//example.test', '/auth/refresh', '/auth/login', '/auth/me#fragment', '/auth\\me']) {
      await expect(client.apiRequest(path)).rejects.toMatchObject({ status: 400 });
    }
    const response = reply(testUser());
    fetchMock.mockResolvedValueOnce(response);
    await client.apiRequest('/auth/me', { headers: { Authorization: 'ignored' } });
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get('Authorization')).not.toBe('ignored');
  });

  it('handles malformed errors and success JSON safely; abort discards a request', async () => {
    await authenticated();
    fetchMock.mockResolvedValueOnce(new Response('not json', { status: 500 }));
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 500 });
    fetchMock.mockResolvedValueOnce(new Response('not json', { status: 200 }));
    await expect(client.apiRequest('/auth/me')).rejects.toMatchObject({ status: 502 });
    const controller = new AbortController();
    controller.abort();
    fetchMock.mockRejectedValueOnce(new DOMException('Aborted', 'AbortError'));
    await expect(client.apiRequest('/auth/me', { signal: controller.signal })).rejects.toBeInstanceOf(client.StaleRequestError);
  });
});
