import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardPage from '../pages/DashboardPage.jsx';
import App from '../App.jsx';
import { TestAuth, testUser } from './authTestHelpers.jsx';
import { dashboardMetrics, getDashboard } from '../services/dashboardService.js';
import { ApiError, StaleRequestError } from '../services/apiClient.js';

vi.mock('../services/dashboardService.js', async (original) => ({ ...await original(), getDashboard: vi.fn() }));
const load = vi.mocked(getDashboard);
/** @param {import('../services/apiClient.js').Role} [role] @param {boolean} [zero] */
function snapshot(role = 'USER', zero = false) {
  const section = Object.fromEntries(dashboardMetrics[role].map(({ key }, index) => [key, zero ? 0 : index + 1]));
  if (role === 'STAFF') Object.assign(section, { employeeId: 2, departmentId: 3 });
  return { role, asOf: '2026-07-06T16:00:00Z', section };
}
/** @param {import('../services/apiClient.js').Role} [role] */
function mount(role = 'USER') {
  return render(<TestAuth value={{ status: 'authenticated', user: testUser(role) }}><DashboardPage /></TestAuth>);
}
function deferred() {
  /** @type {(value: import('../services/dashboardService.js').Dashboard) => void} */
  let resolve = () => {};
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}
beforeEach(() => { load.mockReset(); });

describe('FE-03 role-specific dashboard', () => {
  it.each(['USER', 'STAFF', 'ADMIN'])('renders only exact %s metrics, backend counts and unchanged account panel', async (value) => {
    const role = /** @type {import('../services/apiClient.js').Role} */ (value);
    const data = snapshot(role);
    load.mockResolvedValueOnce(data);
    mount(role);
    const region = await screen.findByRole('region', { name: `${role} dashboard` });
    await screen.findByText('Jul 6, 2026, 12:00 PM');
    expect(region.querySelector('time')?.dateTime).toBe(data.asOf);
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeTruthy();
    expect(region.querySelectorAll('.metric-card').length).toBe(dashboardMetrics[role].length);
    for (const { key, label, description } of dashboardMetrics[role]) {
      const card = within(region).getByRole('heading', { name: label }).parentElement;
      expect(card?.querySelector('.metric-count')?.textContent).toBe(String(data.section[key]));
      expect(card?.textContent).toContain(description);
    }
    expect(load).toHaveBeenCalledTimes(1);
    expect(load.mock.calls[0][0]).toBe(role);
    expect(screen.queryByRole('table')).toBeNull();
    for (const other of ['USER', 'STAFF', 'ADMIN'].filter((item) => item !== role)) {
      expect(screen.queryByRole('region', { name: `${other} dashboard` })).toBeNull();
    }
    if (role !== 'ADMIN') expect(screen.queryByRole('heading', { name: 'Active rooms' })).toBeNull();
    if (role === 'USER') expect(screen.queryByRole('heading', { name: 'Your assigned tasks' })).toBeNull();
  });

  it.each(['USER', 'STAFF', 'ADMIN'])('%s zero counts stay visible with a truthful empty state', async (value) => {
    const role = /** @type {import('../services/apiClient.js').Role} */ (value);
    load.mockResolvedValueOnce(snapshot(role, true));
    mount(role);
    expect(await screen.findByText('All counts in this snapshot are zero.')).toBeTruthy();
    expect(document.querySelectorAll('.metric-count').length).toBe(dashboardMetrics[role].length);
    expect([...document.querySelectorAll('.metric-count')].every((item) => item.textContent === '0')).toBe(true);
  });

  it('loading announces status and skeletons without exposing stale counts', () => {
    load.mockReturnValueOnce(deferred().promise);
    mount('STAFF');
    expect(screen.getByRole('status').textContent).toBe('Loading dashboard…');
    expect(screen.getByRole('region').getAttribute('aria-busy')).toBe('true');
    expect(document.querySelectorAll('.skeleton').length).toBe(6);
    expect(document.querySelector('.metric-count')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeTruthy();
  });

  it.each([0, 403, 429, 500])('safe %s error can be retried with the keyboard', async (status) => {
    load.mockRejectedValueOnce(new ApiError(status)).mockResolvedValueOnce(snapshot('STAFF'));
    mount('STAFF');
    expect((await screen.findByRole('alert')).textContent).toBe(new ApiError(status).message);
    expect(screen.getByRole('heading', { name: 'Dashboard unavailable', level: 2 })).toBeTruthy();
    expect(document.querySelector('.metric-count')).toBeNull();
    const retry = screen.getByRole('button', { name: 'Retry dashboard' });
    retry.focus();
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { name: 'Your assigned tasks' })).toBeTruthy();
    expect(load).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it.each([new Error('Synthetic private details'), new StaleRequestError()])('unexpected/interrupted failure has a safe recovery message', async (failure) => {
    load.mockRejectedValueOnce(failure);
    mount();
    expect((await screen.findByRole('alert')).textContent).not.toContain('Synthetic private details');
    expect(screen.getByRole('button', { name: 'Retry dashboard' })).toBeTruthy();
  });

  it('identity change hides ready data before a new response, and a failed request cannot restore it', async () => {
    load.mockResolvedValueOnce({ ...snapshot(), section: { registrationCount: 97 } });
    const view = mount();
    await screen.findByText('97');
    load.mockRejectedValueOnce(new ApiError(500));
    view.rerender(<TestAuth value={{ status: 'authenticated', user: { ...testUser(), id: 4 } }}><DashboardPage /></TestAuth>);
    expect(screen.queryByText('97')).toBeNull();
    await screen.findByRole('alert');
    expect(document.querySelector('.metric-count')).toBeNull();
    expect(screen.queryByText('97')).toBeNull();
  });

  it.each(['role', 'employee', 'department', 'session'])('%s change cancels old requests and hides previous private state', async (kind) => {
    const first = deferred();
    const second = deferred();
    load.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const view = mount('STAFF');
    const signal = load.mock.calls[0][1];
    const user = testUser('STAFF');
    if (kind === 'role') user.role = 'ADMIN';
    if (kind === 'employee') user.employeeId = 6;
    if (kind === 'department') user.departmentSummary = { id: 4, name: 'New department' };
    view.rerender(<TestAuth value={{ status: 'authenticated', user, sessionKey: kind === 'session' ? 2 : 0 }}><DashboardPage /></TestAuth>);
    expect(signal.aborted).toBe(true);
    await act(async () => { first.resolve({ ...snapshot('STAFF'), section: { nonTerminalAssignedTaskCount: 97 } }); });
    expect(screen.queryByText('97')).toBeNull();
    await act(async () => { second.resolve(snapshot(user.role)); });
    expect(screen.getByRole('region', { name: `${user.role} dashboard` }).getAttribute('aria-busy')).toBe('false');
  });

  it.each(['anonymous', 'recoverable-error'])('%s clears ready private data and unmounts the account view', async (value) => {
    const status = /** @type {import('../services/apiClient.js').AuthState['status']} */ (value);
    load.mockResolvedValueOnce({ ...snapshot(), section: { registrationCount: 97 } });
    const view = mount();
    await screen.findByText('97');
    view.rerender(<TestAuth value={{ status, user: null }}><DashboardPage /></TestAuth>);
    expect(screen.queryByText('97')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Your account' })).toBeNull();
  });

  it('unmount cancels requests and ignores completion', async () => {
    const pending = deferred();
    load.mockReturnValueOnce(pending.promise);
    const view = mount();
    const signal = load.mock.calls[0][1];
    view.unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(snapshot()); });
    expect(screen.queryByRole('heading', { name: 'Your registrations' })).toBeNull();
  });

  it('stale rejection after unmount is ignored', async () => {
    /** @type {(error: Error) => void} */
    let reject = () => {};
    load.mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const view = mount();
    view.unmount();
    await act(async () => { reject(new ApiError(500)); });
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('keeps dashboard and account contents through a keyboard theme switch without refetching', async () => {
    load.mockResolvedValueOnce(snapshot());
    render(<MemoryRouter initialEntries={['/dashboard']}><TestAuth value={{ status: 'authenticated', user: testUser() }}><App /></TestAuth></MemoryRouter>);
    await screen.findByRole('heading', { name: 'Your registrations' });
    const toggle = screen.getByRole('button', { name: 'Switch to dark mode' });
    toggle.focus();
    await userEvent.keyboard('{Enter}');
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(screen.getByRole('heading', { name: 'Your registrations' })).toBeTruthy();
    expect(load).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeTruthy();
  });
});
