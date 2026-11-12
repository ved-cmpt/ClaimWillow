import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { HeroTimeline, LandingPage, REPOSITORY_URL } from '../pages/LandingPage';
import { DEMO_PASSWORD, SLOW_AFTER_MS } from '../features/auth/demo';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

const anonymous = () => jsonResponse({ code: 'UNAUTHENTICATED' }, 401);
const user = (role: string, username: string, displayName: string) =>
  ({ id: username, username, email: `${username}@example.com`, displayName, role });

function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
}

function renderLanding() {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/claims" element={<p>Claims home</p>} />
      <Route path="/staff/queue" element={<p>Staff queue</p>} />
      <Route path="/login" element={<p>Login screen</p>} />
    </Routes>,
    { route: '/' },
  );
}

function stubReducedMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduce && query.includes('reduce'), media: query, onchange: null,
    addEventListener: () => undefined, removeEventListener: () => undefined,
    addListener: () => undefined, removeListener: () => undefined, dispatchEvent: () => false,
  }));
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  clearCsrfCookie();
});

describe('LandingPage', () => {
  it('shows the value proposition, three demo buttons, the cold-start note and the synthetic notice', async () => {
    mockFetch({ 'GET /auth/me': anonymous });
    renderLanding();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('handled calmly and clearly');
    expect(screen.getByTestId('demo-policyholder')).toHaveTextContent('Try as a policyholder (Priya)');
    expect(screen.getByTestId('demo-adjuster')).toHaveTextContent('Try as an adjuster (Dana)');
    expect(screen.getByTestId('demo-admin')).toHaveTextContent('Try as an admin');
    expect(screen.getAllByText(/first load can take a minute or two/).length).toBeGreaterThan(0);
    expect(screen.getByTestId('synthetic-notice')).toHaveTextContent('Synthetic data only.');
    expect(screen.getByTestId('repo-link')).toHaveAttribute('href', REPOSITORY_URL);
    for (const name of ['Five clear steps, one shared record.', 'Six rules, each one explained.',
      'Audit trail, security and tests you can read.']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument();
    }
    for (const step of ['Submit', 'Automated checks', 'Adjuster review', 'Decision', 'Closed']) {
      expect(screen.getByRole('heading', { level: 3, name: step })).toBeInTheDocument();
    }
    const rules = screen.getByRole('heading', { name: 'Six rules, each one explained.' }).closest('section')!;
    for (const code of ['R1', 'R2', 'R3', 'R4', 'R5', 'R6']) expect(within(rules).getByText(code)).toBeInTheDocument();
    expect(await screen.findByTestId('landing-sign-in')).toHaveAttribute('href', '/login');
    expect(screen.queryByTestId('open-app')).not.toBeInTheDocument();
  });

  it('uses landmarks and labelled sections', () => {
    mockFetch({ 'GET /auth/me': anonymous });
    renderLanding();
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Page sections' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Five clear steps, one shared record.' })).toBeInTheDocument();
  });

  it('offers "Open app" to a signed-in user without redirecting away', async () => {
    mockFetch({ 'GET /auth/me': () => jsonResponse(user('ADJUSTER', 'dana', 'Dana Okafor')) });
    renderLanding();
    const open = await screen.findByTestId('open-app');
    expect(open).toHaveAttribute('href', '/staff/queue');
    expect(screen.getByTestId('landing-signed-in')).toHaveTextContent('Dana Okafor');
    expect(screen.getByTestId('landing')).toBeInTheDocument();
    expect(screen.queryByText('Staff queue')).not.toBeInTheDocument();
  });

  it('signs in as Dana through the login API with a fresh CSRF token and opens the work queue', async () => {
    clearCsrfCookie();
    const fetchMock = mockFetch({
      'GET /auth/me': anonymous,
      'GET /auth/csrf': () => {
        document.cookie = 'XSRF-TOKEN=fresh-token';
        return jsonResponse({ headerName: 'X-XSRF-TOKEN' });
      },
      'POST /auth/login': () => jsonResponse(user('ADJUSTER', 'dana', 'Dana Okafor')),
    });
    renderLanding();
    await userEvent.click(screen.getByTestId('demo-adjuster'));
    expect(await screen.findByText('Staff queue')).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/auth/login'))!;
    const init = call[1] as RequestInit;
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('fresh-token');
    expect(JSON.parse(init.body as string)).toEqual({ username: 'dana', password: DEMO_PASSWORD });
  });

  it('sends the policyholder demo to My claims and the admin demo to the work queue', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const bodies: unknown[] = [];
    mockFetch({
      'GET /auth/me': anonymous,
      'POST /auth/login': (_url, init) => {
        const body = JSON.parse(init!.body as string);
        bodies.push(body);
        return jsonResponse(body.username === 'priya' ? user('POLICYHOLDER', 'priya', 'Priya Shah')
          : user('ADMIN', 'admin', 'Avery Admin'));
      },
    });
    const first = renderLanding();
    await userEvent.click(screen.getByTestId('demo-policyholder'));
    expect(await screen.findByText('Claims home')).toBeInTheDocument();
    first.unmount();

    renderLanding();
    await userEvent.click(screen.getByTestId('demo-admin'));
    expect(await screen.findByText('Staff queue')).toBeInTheDocument();
    expect(bodies).toEqual([
      { username: 'priya', password: DEMO_PASSWORD },
      { username: 'admin', password: DEMO_PASSWORD },
    ]);
  });

  it('explains a failed demo sign-in and re-enables the buttons', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockFetch({
      'GET /auth/me': anonymous,
      'POST /auth/login': () =>
        jsonResponse({ code: 'INVALID_CREDENTIALS', message: 'Username or password is incorrect.' }, 401),
    });
    renderLanding();
    await userEvent.click(screen.getByTestId('demo-policyholder'));
    expect(await screen.findByTestId('demo-error')).toHaveTextContent('Username or password is incorrect.');
    expect(screen.getByTestId('demo-error')).toHaveAttribute('role', 'alert');
    expect(screen.getByTestId('demo-policyholder')).toBeEnabled();
  });

  it('mentions the cold start while a demo sign-in is slow, and blocks double clicks', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    document.cookie = 'XSRF-TOKEN=t';
    let logins = 0;
    mockFetch({
      'GET /auth/me': anonymous,
      'POST /auth/login': () => {
        logins += 1;
        return new Promise<Response>(() => undefined);
      },
    });
    renderLanding();
    fireEvent.click(screen.getByTestId('demo-adjuster'));
    await waitFor(() => expect(screen.getByTestId('demo-adjuster')).toHaveAttribute('aria-busy', 'true'));
    expect(screen.getByTestId('demo-policyholder')).toBeDisabled();
    fireEvent.click(screen.getByTestId('demo-adjuster'));
    expect(screen.queryByTestId('demo-waking')).not.toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(SLOW_AFTER_MS + 100); });
    expect(screen.getByTestId('demo-waking')).toHaveTextContent('first load can take a minute or two');
    expect(logins).toBe(1);
  });
});

describe('HeroTimeline', () => {
  it('can be paused, and the pause control reports its state', async () => {
    stubReducedMotion(false);
    renderWithProviders(<HeroTimeline />);
    const pause = screen.getByTestId('timeline-pause');
    expect(pause).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(pause);
    expect(pause).toHaveAttribute('aria-pressed', 'true');
    expect(pause).toHaveTextContent('Play animation');
    expect(screen.getByTestId('hero-timeline')).toHaveTextContent('Closed');
  });

  it('shows the finished claim without animation when reduced motion is preferred', () => {
    stubReducedMotion(true);
    renderWithProviders(<HeroTimeline />);
    expect(screen.queryByTestId('timeline-pause')).not.toBeInTheDocument();
    expect(screen.getByTestId('hero-timeline')).toHaveTextContent('Closed');
  });
});
