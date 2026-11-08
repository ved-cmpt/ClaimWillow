import { describe, expect, it, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { LoginPage, validateLogin } from '../pages/LoginPage';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

function renderLogin() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/claims" element={<p>Claims home</p>} />
      <Route path="/staff/queue" element={<p>Staff queue</p>} />
    </Routes>,
    { route: '/login' },
  );
}

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    const fetchMock = mockFetch({ 'GET /auth/me': () => jsonResponse({ code: 'UNAUTHENTICATED' }, 401) });
    renderLogin();
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Email/)).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/login'))).toBe(false);
  });

  it('shows the server error message for bad credentials', async () => {
    mockFetch({
      'GET /auth/me': () => jsonResponse({ code: 'UNAUTHENTICATED' }, 401),
      'GET /auth/csrf': () => jsonResponse({ headerName: 'X-XSRF-TOKEN' }),
      'POST /auth/login': () =>
        jsonResponse({ code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.' }, 401),
    });
    renderLogin();
    await userEvent.type(await screen.findByLabelText(/Email/), 'priya@example.com');
    await userEvent.type(screen.getByLabelText(/Password/), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByTestId('login-error')).toHaveTextContent('Email or password is incorrect.');
  });

  it('sends credentials with the CSRF header and routes staff to the queue', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';
    const fetchMock = mockFetch({
      'GET /auth/me': () => jsonResponse({ code: 'UNAUTHENTICATED' }, 401),
      'POST /auth/login': () =>
        jsonResponse({ id: '1', email: 'dana@example.com', displayName: 'Dana', role: 'ADJUSTER' }),
    });
    renderLogin();
    await userEvent.type(await screen.findByLabelText(/Email/), 'dana@example.com');
    await userEvent.type(screen.getByLabelText(/Password/), 'secret-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(screen.getByText('Staff queue')).toBeInTheDocument());
    const loginCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/auth/login'))!;
    const init = loginCall[1] as RequestInit;
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123');
    expect(JSON.parse(init.body as string)).toEqual({ email: 'dana@example.com', password: 'secret-password' });
  });

  it('validateLogin rejects malformed email', () => {
    expect(validateLogin('nope', 'x').email).toBe('Enter a valid email address.');
    expect(validateLogin('a@b.co', 'x')).toEqual({});
  });
});
