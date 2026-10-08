import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { TestAuth, testUser } from './authTestHelpers.jsx';
import { ApiError, StaleRequestError } from '../services/apiClient.js';
import { routeDefinitions } from '../routes/routeDefinitions.js';

// Auth/account UI checks isolate the independently tested dashboard request.
vi.mock('../services/housekeepingService.js', async (original) => ({
  ...await original(), listTasks: vi.fn(() => new Promise(() => {})), listRooms: vi.fn(() => new Promise(() => {})),
}));

/** @param {string} path @param {Partial<import('../context/authContext.js').AuthContextValue>} [value] */
function renderApp(path, value = {}) {
  return render(<MemoryRouter initialEntries={[path]}><TestAuth value={value}><App /></TestAuth></MemoryRouter>);
}

describe('FE-02 forms and guards', () => {
  it('blocks protected content during bootstrap without mounting private children', () => {
    renderApp('/team', { status: 'checking' });
    expect(screen.getByRole('status').textContent).toContain('Checking your session');
    expect(screen.queryByRole('heading', { name: 'Team' })).toBeNull();
  });

  it('shows recoverable unavailable state and permits explicit retry', async () => {
    const retry = vi.fn(async () => {});
    renderApp('/dashboard', { status: 'recoverable-error', error: new ApiError(429), retry });
    expect(screen.getByRole('heading', { name: 'Session unavailable' })).toBeTruthy();
    expect(screen.getByText(/not been confirmed as signed out/)).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Retry session check' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('redirects an anonymous protected deep link to sign in', () => {
    renderApp('/tasks');
    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Task details' })).toBeNull();
  });

  it.each(['/login', '/register', '/'])('authenticated %s redirects to dashboard with a read-only profile', (path) => {
    renderApp(path, { status: 'authenticated', user: testUser() });
    expect(screen.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'My account' })).toBeTruthy();
    expect(screen.getByText('reviewer@example.test')).toBeTruthy();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeTruthy();
  });

  it.each(['USER', 'MANAGER', 'ADMIN'])('%s sees only role-eligible navigation', (role) => {
    const user = testUser(/** @type {import('../routes/routeDefinitions.js').Role} */ (role));
    renderApp('/dashboard', { status: 'authenticated', user });
    for (const route of routeDefinitions.filter((item) => item.access === 'protected' && !item.path.includes(':'))) {
      expect(screen.queryByRole('link', { name: route.path === '/tasks' && user.role === 'USER' ? 'My Tasks' : route.title }) !== null).toBe(route.roles.includes(user.role));
    }
    expect(screen.queryByRole('link', { name: 'Create an account' })).toBeNull();
  });

  it.each(routeDefinitions.filter((item) => item.access === 'protected' && !item.roles.includes('USER')))(
    'USER cannot mount $path or make operational requests', (route) => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch');
      renderApp(route.path.replace(/:[A-Za-z]+/, '1'), { status: 'authenticated', user: testUser() });
      expect(screen.getByRole('heading', { name: 'Access denied' })).toBeTruthy();
      expect(screen.queryByRole('heading', { name: route.title })).toBeNull();
      expect(fetchSpy).not.toHaveBeenCalled();
    },
  );



  it('validates empty fields with accessible associations and focuses the error summary', async () => {
    const login = vi.fn(async () => {});
    renderApp('/login', { login });
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(login).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Email (required)').getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('Password (required)').getAttribute('aria-describedby')).toContain('auth-password-error');
    expect(document.activeElement).toBe(screen.getByText('Check the highlighted fields.'));
  });

  it('uses keyboard form submission, clears password, and sends only login fields', async () => {
    const login = vi.fn(async () => {});
    renderApp('/login', { login });
    const password = crypto.randomUUID();
    await userEvent.type(screen.getByLabelText('Email (required)'), 'synthetic@example.test');
    await userEvent.type(screen.getByLabelText('Password (required)'), password);
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(login).toHaveBeenCalledWith({ email: 'synthetic@example.test', password }));
    expect(/** @type {HTMLInputElement} */ (screen.getByLabelText('Password (required)')).value).toBe('');
  });

  it('public registration has no role selector and validates name/password lengths', async () => {
    const register = vi.fn(async () => {});
    renderApp('/register', { register });
    expect(screen.queryByRole('combobox')).toBeNull();
    await userEvent.type(screen.getByLabelText('Name (required)'), 'A');
    await userEvent.type(screen.getByLabelText('Email (required)'), 'synthetic@example.test');
    await userEvent.type(screen.getByLabelText('Password (required)'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Create housekeeper account' }));
    expect(register).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Name (required)').getAttribute('aria-invalid')).toBe('true');
    await userEvent.clear(screen.getByLabelText('Name (required)'));
    await userEvent.type(screen.getByLabelText('Name (required)'), 'Synthetic reviewer');
    await userEvent.clear(screen.getByLabelText('Password (required)'));
    const password = crypto.randomUUID();
    await userEvent.type(screen.getByLabelText('Password (required)'), password);
    await userEvent.click(screen.getByRole('button', { name: 'Create housekeeper account' }));
    expect(register).toHaveBeenCalledWith({ name: 'Synthetic reviewer', email: 'synthetic@example.test', password });
  });

  it('renders safe API field errors, clears password and preserves focus', async () => {
    const login = vi.fn(async () => { throw new ApiError(400, { email: 'Check this value.' }); });
    renderApp('/login', { login });
    await userEvent.type(screen.getByLabelText('Email (required)'), 'synthetic@example.test');
    await userEvent.type(screen.getByLabelText('Password (required)'), crypto.randomUUID());
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Check this value.')).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByText('Check the form fields and try again.'));
    expect(/** @type {HTMLInputElement} */ (screen.getByLabelText('Password (required)')).value).toBe('');
  });

  it('prevents duplicate submits while pending and ignores stale completion', async () => {
    /** @type {(value?: unknown) => void} */
    let done = () => {};
    const promise = new Promise((resolve) => { done = resolve; });
    const login = vi.fn(() => promise.then(() => { throw new StaleRequestError(); }));
    renderApp('/login', { login });
    await userEvent.type(screen.getByLabelText('Email (required)'), 'synthetic@example.test');
    await userEvent.type(screen.getByLabelText('Password (required)'), crypto.randomUUID());
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(/** @type {HTMLButtonElement} */ (screen.getByRole('button', { name: 'Signing in…' })).disabled).toBe(true);
    await userEvent.keyboard('{Enter}');
    expect(login).toHaveBeenCalledTimes(1);
    done();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeTruthy());
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('failed server logout displays no false success and offers retry', async () => {
    const logout = vi.fn(async () => { throw new ApiError(0); });
    renderApp('/dashboard', { status: 'authenticated', user: testUser('MANAGER'), logout });
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(screen.getByRole('alert').textContent).toContain('Sign-out could not be confirmed');
    expect(document.activeElement).toBe(screen.getByRole('alert'));
    expect(screen.getByText('reviewer@example.test')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeTruthy();
  });

  it('a session-key change remounts private page state and hides it on logout', () => {
    const user = testUser();
    const view = renderApp('/dashboard', { status: 'authenticated', user, sessionKey: 1 });
    const oldPanel = screen.getByRole('heading', { name: 'My account' });
    view.rerender(<MemoryRouter initialEntries={['/dashboard']}><TestAuth value={{ status: 'authenticated', user, sessionKey: 2 }}><App /></TestAuth></MemoryRouter>);
    expect(screen.getByRole('heading', { name: 'My account' })).not.toBe(oldPanel);
    view.rerender(<MemoryRouter><TestAuth><App /></TestAuth></MemoryRouter>);
    expect(screen.queryByText('reviewer@example.test')).toBeNull();
  });
});
