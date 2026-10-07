import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { routeDefinitions } from '../routes/routeDefinitions.js';
import { TestAuth, testUser } from './authTestHelpers.jsx';
import * as dashboardService from '../services/dashboardService.js';

/** @param {string} path */
function renderRoute(path, role = /** @type {import('../routes/routeDefinitions.js').Role | null} */ (null)) {
  return render(<MemoryRouter initialEntries={[path]}><TestAuth value={role ? { status: 'authenticated', user: testUser(role) } : {}}><App /></TestAuth></MemoryRouter>);
}

describe('FE-01 routes', () => {
  it('redirects anonymous root visits to the public sign-in form', () => {
    renderRoute('/');
    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBeTruthy();
    expect(screen.getByLabelText('Email (required)')).toBeTruthy();
    expect(screen.getByLabelText('Password (required)').getAttribute('type')).toBe('password');
  });

  it('supports public navigation and marks the current link', async () => {
    renderRoute('/login');
    const register = screen.getByRole('link', { name: 'Create an account' });
    await userEvent.click(register);
    expect(screen.getByRole('heading', { name: 'Create an account', level: 1 })).toBeTruthy();
    expect(register.getAttribute('aria-current')).toBe('page');
  });

  it('provides accessible mobile-menu controls and closes them after navigation', async () => {
    renderRoute('/login');
    // CSS breakpoints are checked in a browser; jsdom tests only the state wiring.
    const menu = screen.getByRole('button', { name: 'Menu' });
    expect(menu.getAttribute('aria-expanded')).toBe('false');
    await userEvent.click(menu);
    expect(menu.getAttribute('aria-expanded')).toBe('true');
    expect(menu.getAttribute('aria-controls')).toBe('main-navigation');
    await userEvent.click(screen.getByRole('link', { name: 'Create an account' }));
    expect(menu.getAttribute('aria-expanded')).toBe('false');
  });

  it.each(routeDefinitions.filter((route) => route.access === 'protected' && !['/dashboard', '/admin/employees', '/admin/departments'].includes(route.path)))(
    '$path is a scaffold with no data fetch, credentials, or token storage',
    (route) => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch');
      const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
      renderRoute(route.path.replace(/:[A-Za-z]+/, '1'), route.roles[0]);
      expect(screen.getByRole('heading', { name: route.title, level: 1 })).toBeTruthy();
      expect(screen.getByText(/No hotel operational data is loaded or displayed/)).toBeTruthy();
      expect(screen.queryByRole('table')).toBeNull();
      expect(screen.queryByRole('textbox')).toBeNull();
      expect(fetchSpy).not.toHaveBeenCalled();
      expect(storageSpy).not.toHaveBeenCalled();
    },
  );

  it('only contains canonical role names and the expected public routes', () => {
    expect(routeDefinitions.filter((route) => route.access === 'public').map((route) => route.path)).toEqual(['/login', '/register']);
    expect(new Set(routeDefinitions.map((route) => route.path)).size).toBe(routeDefinitions.length);
    for (const route of routeDefinitions.filter((item) => item.access === 'protected')) {
      expect(route.roles.length).toBeGreaterThan(0);
      expect(route.roles.every((role) => ['USER', 'STAFF', 'ADMIN'].includes(role))).toBe(true);
    }
    expect(routeDefinitions.find((route) => route.path === '/registrations')?.roles).toEqual(['USER']);
    expect(routeDefinitions.find((route) => route.path === '/analytics')?.roles).toEqual(['ADMIN']);
  });

  it('dashboard now mounts the role-specific view and retains the account without operational CRUD', async () => {
    const load = vi.spyOn(dashboardService, 'getDashboard').mockResolvedValueOnce({
      role: 'USER', asOf: '2026-07-06T16:00:00Z', section: { registrationCount: 0 },
    });
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    renderRoute('/dashboard', 'USER');
    expect(await screen.findByRole('heading', { name: 'Your registrations' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeTruthy();
    expect(load).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(storage).not.toHaveBeenCalled();
  });

  it('renders a safe unknown-route page and a public recovery link', async () => {
    renderRoute('/unknown');
    expect(screen.getByRole('heading', { name: 'This page isn’t here.' })).toBeTruthy();
    await userEvent.click(screen.getByRole('link', { name: 'Back to sign in' }));
    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBeTruthy();
  });

  it('includes a main landmark, skip link, branding and the approved hotel zone', async () => {
    renderRoute('/login');
    expect(screen.getByRole('main').id).toBe('main-content');
    expect(screen.getByRole('link', { name: 'Skip to content' }).getAttribute('href')).toBe('#main-content');
    expect(screen.getByText('Hotel time zone: America/New_York')).toBeTruthy();
    await userEvent.click(screen.getByRole('link', { name: 'Rockey home' }));
    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBeTruthy();
  });
});
