import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/types';
import { useCurrentUser, useLogout } from '../features/auth/useAuth';

export function Wordmark({ className = '' }: { className?: string }) {
  return (
    <span className={`flex items-center gap-2 font-semibold tracking-tight text-ink ${className}`}>
      <svg aria-hidden="true" viewBox="0 0 32 32" className="h-7 w-7">
        <rect width="32" height="32" rx="7" fill="#0f5f5c" />
        <path d="M9 22c3-9 7-12 14-13-4 3-6 7-7 13" fill="none" stroke="#f7f5f0" strokeWidth="2.4"
          strokeLinecap="round" />
      </svg>
      <span className="text-lg">ClaimWillow</span>
    </span>
  );
}

interface NavItem {
  to: string;
  label: string;
  testId: string;
}

export function navItemsFor(user: CurrentUser | null | undefined): NavItem[] {
  if (!user) return [];
  if (user.role === 'POLICYHOLDER') {
    return [
      { to: '/claims', label: 'My claims', testId: 'nav-my-claims' },
      { to: '/policies', label: 'My policies', testId: 'nav-policies' },
    ];
  }
  const items: NavItem[] = [
    { to: '/staff/queue', label: 'Work queue', testId: 'nav-queue' },
    { to: '/reports', label: 'Reports', testId: 'nav-reports' },
  ];
  if (user.role === 'ADMIN') items.push({ to: '/admin/users', label: 'Accounts', testId: 'nav-admin' });
  return items;
}

const roleLabel: Record<CurrentUser['role'], string> = {
  POLICYHOLDER: 'Policyholder',
  ADJUSTER: 'Claims adjuster',
  ADMIN: 'Administrator',
};

export function AppShell() {
  const me = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();
  const user = me.data;

  const signOut = () => {
    logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) });
  };

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50
        focus:rounded focus:bg-surface focus:px-3 focus:py-2">Skip to content</a>
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-3">
          <div className="flex flex-wrap items-center gap-x-8 gap-y-2">
            <Wordmark />
            {user && (
              <nav aria-label="Primary" className="flex gap-1">
                {navItemsFor(user).map((item) => (
                  <NavLink key={item.to} to={item.to} data-testid={item.testId}
                    className={({ isActive }) => `rounded-md px-3 py-1.5 text-sm font-medium ${isActive
                      ? 'bg-accent-soft text-accent-strong' : 'text-ink-muted hover:bg-subtle hover:text-ink'}`}>
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            )}
          </div>
          {user && (
            <div className="flex items-center gap-3">
              <div className="text-right leading-tight">
                <p className="text-sm font-medium text-ink" data-testid="current-user-name">{user.displayName}</p>
                <p className="text-xs text-ink-faint">{roleLabel[user.role]}</p>
              </div>
              <button type="button" onClick={signOut} data-testid="sign-out"
                className="rounded-md border border-line-strong px-3 py-1.5 text-sm text-ink hover:bg-subtle">
                Sign out
              </button>
            </div>
          )}
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="mx-auto w-full max-w-6xl px-4 pb-8 text-xs text-ink-faint">
        Synthetic data only. ClaimWillow is a portfolio application, not a licensed insurance platform.
        Do not enter real personal or insurance information.
      </footer>
    </div>
  );
}
