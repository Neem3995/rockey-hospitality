import { StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import AuthProvider from '../context/AuthProvider.jsx';
import useAuth from '../hooks/useAuth.js';
import App from '../App.jsx';
import { testUser } from './authTestHelpers.jsx';

function Inspector() {
  const auth = useAuth();
  return <output data-testid="context">{JSON.stringify({ ...auth, login: undefined, register: undefined, logout: undefined, retry: undefined })}</output>;
}

it('StrictMode provider shares bootstrap, publishes safe context and unsubscribes on unmount', async () => {
  const token = crypto.randomUUID();
  const mock = vi.spyOn(globalThis, 'fetch')
    .mockImplementation(async () => new Response(JSON.stringify({ role: 'USER', asOf: '2026-07-06T16:00:00Z', user: { registrationCount: 0 } }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: token, tokenType: 'Bearer', accessExpiresAt: new Date(Date.now() + 60000).toISOString() }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify(testUser()), { status: 200 }));
  const storage = vi.spyOn(Storage.prototype, 'setItem');
  const view = render(<StrictMode><MemoryRouter initialEntries={['/login']}><AuthProvider><App /><Inspector /></AuthProvider></MemoryRouter></StrictMode>);
  expect(await screen.findByRole('heading', { name: 'Your account' })).toBeTruthy();
  expect(await screen.findByRole('heading', { name: 'Your registrations' })).toBeTruthy();
  expect(mock.mock.calls.filter(([url]) => String(url).includes('/auth/')).length).toBe(2);
  const context = screen.getByTestId('context').textContent;
  expect(context).toContain('authenticated');
  expect(context).not.toContain(token);
  expect(context).not.toContain('accessToken');
  expect(storage).not.toHaveBeenCalled();
  mock.mockResolvedValueOnce(new Response(null, { status: 204 }));
  await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
  expect(await screen.findByRole('heading', { name: 'Sign in', level: 1 })).toBeTruthy();
  expect(screen.queryByText('reviewer@example.test')).toBeNull();
  expect(screen.getByTestId('context').textContent).toContain('anonymous');
  view.unmount();
});
