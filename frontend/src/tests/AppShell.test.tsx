import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { AppShell, initials } from '../components/AppShell';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

function renderShell(role: string) {
  mockFetch({
    'GET /auth/me': () => jsonResponse({ id: '1', username: 'u', email: 'u@example.com', displayName: 'Dana Okafor', role }),
  });
  return renderWithProviders(
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/staff/queue" element={<p>Queue page</p>} />
        <Route path="/claims" element={<p>Claims page</p>} />
      </Route>
    </Routes>,
    { route: role === 'POLICYHOLDER' ? '/claims' : '/staff/queue' },
  );
}

describe('AppShell', () => {
  it('renders a labelled workspace sidebar with role navigation and the signed-in user', async () => {
    renderShell('ADMIN');
    expect(await screen.findByTestId('current-user-name')).toHaveTextContent('Dana Okafor');
    const nav = screen.getByRole('navigation', { name: 'Primary' });
    expect(nav).toHaveTextContent('Work queue');
    expect(nav).toHaveTextContent('Reports');
    expect(nav).toHaveTextContent('Accounts');
    expect(screen.getByTestId('nav-queue')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('main')).toHaveTextContent('Queue page');
    expect(screen.getByTestId('sign-out')).toBeInTheDocument();
  });

  it('offers a new-claim shortcut to policyholders only', async () => {
    renderShell('POLICYHOLDER');
    expect(await screen.findByTestId('nav-new-claim')).toHaveAttribute('href', '/claims/new');
    expect(screen.queryByTestId('nav-queue')).not.toBeInTheDocument();
  });

  it('opens the navigation drawer from the menu button and closes it with Escape', async () => {
    renderShell('ADJUSTER');
    await screen.findByTestId('current-user-name');
    const menu = screen.getByTestId('open-menu');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveAttribute('aria-controls', 'app-sidebar');
    await userEvent.click(menu);
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    await waitFor(() => expect(document.getElementById('app-sidebar')).toContainElement(
      document.activeElement as HTMLElement));
    await userEvent.keyboard('{Escape}');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveFocus();
  });

  it('builds avatar initials from display names', () => {
    expect(initials('Dana Okafor')).toBe('DO');
    expect(initials('admin')).toBe('A');
    expect(initials('')).toBe('?');
  });
});
