import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { navItemsFor } from '../components/AppShell';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

const routes = (
  <Routes>
    <Route path="/login" element={<p>Login screen</p>} />
    <Route path="/claims" element={<p>Claims home</p>} />
    <Route element={<ProtectedRoute roles={['ADJUSTER', 'ADMIN']} />}>
      <Route path="/staff/queue" element={<p>Staff queue</p>} />
    </Route>
  </Routes>
);

describe('ProtectedRoute', () => {
  it('redirects anonymous users to sign in', async () => {
    mockFetch({ 'GET /auth/me': () => jsonResponse({ code: 'UNAUTHENTICATED' }, 401) });
    renderWithProviders(routes, { route: '/staff/queue' });
    expect(await screen.findByText('Login screen')).toBeInTheDocument();
  });

  it('sends a policyholder away from staff routes', async () => {
    mockFetch({
      'GET /auth/me': () => jsonResponse({ id: '1', username: 'p', email: 'p@example.com', displayName: 'P', role: 'POLICYHOLDER' }),
    });
    renderWithProviders(routes, { route: '/staff/queue' });
    expect(await screen.findByText('Claims home')).toBeInTheDocument();
  });

  it('lets staff in', async () => {
    mockFetch({
      'GET /auth/me': () => jsonResponse({ id: '2', username: 'd', email: 'd@example.com', displayName: 'D', role: 'ADJUSTER' }),
    });
    renderWithProviders(routes, { route: '/staff/queue' });
    expect(await screen.findByText('Staff queue')).toBeInTheDocument();
  });
});

describe('navItemsFor', () => {
  it('shows role-appropriate navigation', () => {
    expect(navItemsFor({ id: '1', username: '', email: '', displayName: '', role: 'POLICYHOLDER' }).map((i) => i.label))
      .toEqual(['My claims', 'My policies']);
    expect(navItemsFor({ id: '1', username: '', email: '', displayName: '', role: 'ADMIN' }).map((i) => i.label))
      .toContain('Accounts');
    expect(navItemsFor(null)).toEqual([]);
  });
});
