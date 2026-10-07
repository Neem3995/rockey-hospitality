import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import lightLogo from '../assets/brand/Light_Mode_Logo.png';
import darkLogo from '../assets/brand/Dark_Mode_Logo.png';
import { TestAuth } from './authTestHelpers.jsx';

function renderApp() {
  return render(<MemoryRouter initialEntries={['/login']}><TestAuth><App /></TestAuth></MemoryRouter>);
}

beforeEach(() => {
  localStorage.removeItem('rockey-theme');
  delete document.documentElement.dataset.theme;
  document.documentElement.style.colorScheme = '';
});
afterEach(() => {
  vi.restoreAllMocks();
  localStorage.removeItem('rockey-theme');
});

describe('FE-01B theme', () => {
  it('defaults to light with semantic document state and named native control', () => {
    const write = vi.spyOn(Storage.prototype, 'setItem');
    const read = vi.spyOn(Storage.prototype, 'getItem');
    renderApp();
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
    const button = screen.getByRole('button', { name: 'Switch to dark mode' });
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('title')).toBe('Switch to dark mode');
    expect(write).not.toHaveBeenCalled();
    expect(read.mock.calls).toEqual([['rockey-theme']]);
  });

  it('toggles both ways without reloading and persists only the UI preference', async () => {
    const write = vi.spyOn(Storage.prototype, 'setItem');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const sessionAccess = vi.spyOn(window, 'sessionStorage', 'get');
    renderApp();
    const heading = screen.getByRole('heading', { name: 'Sign in', level: 1 });
    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(localStorage.getItem('rockey-theme')).toBe('dark');
    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
    expect(screen.getByRole('heading', { name: 'Sign in', level: 1 })).toBe(heading);
    expect(write.mock.calls).toEqual([['rockey-theme', 'dark'], ['rockey-theme', 'light']]);
    expect(write.mock.contexts.every((context) => context === window.localStorage)).toBe(true);
    expect(sessionAccess).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it.each(['dark', 'light'])('restores saved %s preference on mount', (theme) => {
    localStorage.setItem('rockey-theme', theme);
    renderApp();
    expect(document.documentElement.dataset.theme).toBe(theme);
  });

  it.each(['', 'invalid', 'DARK', 'system'])('invalid saved preference %j safely defaults to light', (value) => {
    localStorage.setItem('rockey-theme', value);
    renderApp();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('starts light when storage reads fail and still supports the toggle', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Denied'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Denied'); });
    renderApp();
    expect(document.documentElement.dataset.theme).toBe('light');
    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('falls back to light when accessing the storage object itself is denied', () => {
    vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => { throw new Error('Denied'); });
    renderApp();
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('supports Enter and Space activation with focus retained', async () => {
    renderApp();
    const toggle = screen.getByRole('button', { name: 'Switch to dark mode' });
    const user = userEvent.setup();
    await user.tab(); // Skip link
    await user.tab(); // Home link
    await user.tab(); // Theme toggle
    expect(document.activeElement).toBe(toggle);
    await user.keyboard('{Enter}');
    expect(document.documentElement.dataset.theme).toBe('dark');
    await user.keyboard(' ');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.activeElement).toBe(toggle);
  });

  it('uses the provided logo URLs with theme selectors and accessible home name', () => {
    renderApp();
    const home = screen.getByRole('link', { name: 'Rockey home' });
    expect(home.querySelector('.logo-light')?.getAttribute('src')).toBe(lightLogo);
    expect(home.querySelector('.logo-dark')?.getAttribute('src')).toBe(darkLogo);
    for (const image of home.querySelectorAll('img')) {
      expect(image.getAttribute('alt')).toBe(''); // Home link already names the brand.
      expect(image.getAttribute('width')).toBe(image.getAttribute('height'));
    }
    // Visibility of theme selectors is verified in CSS checks and real browser QA.
  });
});
