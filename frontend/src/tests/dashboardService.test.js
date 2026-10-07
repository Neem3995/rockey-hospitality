import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { testUser } from './authTestHelpers.jsx';
import { formatHotelTime } from '../utils/hotelTime.js';

/** @type {typeof import('../services/apiClient.js')} */
let client;
/** @type {typeof import('../services/dashboardService.js')} */
let dashboard;
let fetchMock = vi.fn();
/** @param {unknown} data @param {number} [status] */
const reply = (data, status = 200) => new Response(JSON.stringify(data), { status });
const session = () => ({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', accessExpiresAt: new Date(Date.now() + 60000).toISOString() });
/** @param {import('../services/apiClient.js').Role} [role] */
function response(role = 'USER') {
  const section = Object.fromEntries(dashboard.dashboardMetrics[role].map(({ key }, index) => [key, index + 1]));
  if (role === 'STAFF') Object.assign(section, { employeeId: 2, departmentId: 3 });
  return { role, asOf: '2026-07-06T16:00:00Z', [role.toLowerCase()]: section };
}
beforeEach(async () => {
  vi.resetModules();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  client = await import('../services/apiClient.js');
  dashboard = await import('../services/dashboardService.js');
  fetchMock.mockResolvedValueOnce(reply(session())).mockResolvedValueOnce(reply(testUser()));
  await client.bootstrapAuth();
  fetchMock.mockClear();
});
afterEach(() => vi.unstubAllGlobals());

describe('FE-03 frozen dashboard adapter and auth integration', () => {
  it.each(['USER', 'STAFF', 'ADMIN'])('consumes exact %s fields without lists, query params or storage', async (value) => {
    const role = /** @type {import('../services/apiClient.js').Role} */ (value);
    const body = response(role);
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    fetchMock.mockResolvedValueOnce(reply(body));
    const signal = new AbortController().signal;
    expect(await dashboard.getDashboard(role, signal)).toEqual({ role, asOf: body.asOf, section: body[role.toLowerCase()] });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/analytics/dashboard');
    expect(options.credentials).toBe('include');
    expect(options.headers.get('Authorization').startsWith('Bearer ')).toBe(true);
    expect(options.body).toBeUndefined();
    expect(options.signal.aborted).toBe(false);
    expect(storage).not.toHaveBeenCalled();
  });

  it('strips unknown fields from selected-section view state', async () => {
    const body = { ...response(), user: { registrationCount: 4, password: crypto.randomUUID(), activeRoomCount: 99 } };
    fetchMock.mockResolvedValueOnce(reply(body));
    expect((await dashboard.getDashboard('USER', new AbortController().signal)).section).toEqual({ registrationCount: 4 });
  });

  it.each([
    null, [], {}, { ...responseShape(), role: 'ADMIN' },
    { ...responseShape(), admin: { activeRoomCount: 99 } },
    { ...responseShape(), asOf: 'not a date' },
    { ...responseShape(), asOf: '2026-07-06T16:00:00' },
    { ...responseShape(), user: [] }, { ...responseShape(), user: {} },
    ...[-1, 1.5, '4', null, Number.MAX_SAFE_INTEGER + 1].map((count) => ({ ...responseShape(), user: { registrationCount: count } })),
  ])('rejects malformed/mismatched payload %# without rendering other-role data', async (body) => {
    fetchMock.mockResolvedValueOnce(reply(body));
    await expect(dashboard.getDashboard('USER', new AbortController().signal)).rejects.toMatchObject({ status: 502 });
  });

  it.each(['employeeId', 'departmentId'])('requires a valid STAFF %s', async (key) => {
    const body = response('STAFF');
    /** @type {Record<string, number>} */ (body.staff)[key] = 0;
    fetchMock.mockResolvedValueOnce(reply(body));
    await expect(dashboard.getDashboard('STAFF', new AbortController().signal)).rejects.toMatchObject({ status: 502 });
  });

  it.each([403, 429, 500])('%s does not trigger refresh and permits a later dashboard retry', async (status) => {
    fetchMock.mockResolvedValueOnce(reply({}, status));
    await expect(dashboard.getDashboard('USER', new AbortController().signal)).rejects.toMatchObject({ status });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    fetchMock.mockResolvedValueOnce(reply(response()));
    expect((await dashboard.getDashboard('USER', new AbortController().signal)).section.registrationCount).toBe(1);
    expect(fetchMock.mock.calls.every(([url]) => url.endsWith('/analytics/dashboard'))).toBe(true);
  });

  it('401 shares the existing refresh/me flow and retries the same dashboard once', async () => {
    fetchMock.mockResolvedValueOnce(reply({}, 401)).mockResolvedValueOnce(reply(session()))
      .mockResolvedValueOnce(reply(testUser())).mockResolvedValueOnce(reply(response()));
    expect((await dashboard.getDashboard('USER', new AbortController().signal)).section.registrationCount).toBe(1);
    expect(fetchMock.mock.calls.map(([url]) => url.replace('http://localhost:8080/api', '')))
      .toEqual(['/analytics/dashboard', '/auth/refresh', '/auth/me', '/analytics/dashboard']);
  });

  it('network failure is safe and recoverable', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Synthetic offline'));
    await expect(dashboard.getDashboard('USER', new AbortController().signal)).rejects.toMatchObject({ status: 0 });
    fetchMock.mockResolvedValueOnce(reply(response()));
    expect((await dashboard.getDashboard('USER', new AbortController().signal)).section.registrationCount).toBe(1);
  });

  it('caller cancellation discards a completed stale response', async () => {
    /** @type {(value: Response) => void} */
    let done = () => {};
    fetchMock.mockImplementationOnce(() => new Promise((resolve) => { done = resolve; }));
    const controller = new AbortController();
    const result = dashboard.getDashboard('USER', controller.signal);
    const assertion = expect(result).rejects.toBeInstanceOf(client.StaleRequestError);
    controller.abort();
    done(reply(response()));
    await assertion;
  });
});

function responseShape() { return { role: 'USER', asOf: '2026-07-06T16:00:00Z', user: { registrationCount: 1 } }; }

it('displays offset-bearing instants in New York through summer/winter DST without changing the source', () => {
  expect(formatHotelTime('2026-07-06T16:00:00Z')).toBe('Jul 6, 2026, 12:00 PM');
  expect(formatHotelTime('2026-01-06T16:00:00Z')).toBe('Jan 6, 2026, 11:00 AM');
  expect(formatHotelTime('2026-07-06T12:00:00-04:00')).toBe(formatHotelTime('2026-07-06T16:00:00Z'));
});
