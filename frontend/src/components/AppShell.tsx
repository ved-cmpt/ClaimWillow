import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '../api/types';
import { useCurrentUser, useLogout } from '../features/auth/useAuth';
import { Icon } from './Icon';
import type { IconName } from './Icon';

export function WillowMark({ size = 28 }: { size?: number }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32" width={size} height={size} className="shrink-0">
      <rect width="32" height="32" rx="8" fill="#0e6b61" />
      <path d="M9 22c3-9 7-12 14-13-4 3-6 7-7 13" fill="none" stroke="#fbfaf8" strokeWidth="2.4"
        strokeLinecap="round" />
    </svg>
  );
}

export function Wordmark({ className = '' }: { className?: string }) {
  return (
    <span className={`flex items-center gap-2 font-semibold tracking-tight text-ink ${className}`}>
      <WillowMark />
      <span className="text-[17px]">ClaimWillow</span>
    </span>
  );
}

interface NavItem {
  to: string;
  label: string;
  testId: string;
  icon: IconName;
}

export function navItemsFor(user: CurrentUser | null | undefined): NavItem[] {
  if (!user) return [];
  if (user.role === 'POLICYHOLDER') {
    return [
      { to: '/claims', label: 'My claims', testId: 'nav-my-claims', icon: 'inbox' },
      { to: '/policies', label: 'My policies', testId: 'nav-policies', icon: 'shield' },
    ];
  }
  const items: NavItem[] = [
    { to: '/staff/queue', label: 'Work queue', testId: 'nav-queue', icon: 'inbox' },
    { to: '/reports', label: 'Reports', testId: 'nav-reports', icon: 'chart' },
  ];
  if (user.role === 'ADMIN') items.push({ to: '/admin/users', label: 'Accounts', testId: 'nav-admin', icon: 'users' });
  return items;
}

export const roleLabel: Record<CurrentUser['role'], string> = {
  POLICYHOLDER: 'Policyholder',
  ADJUSTER: 'Claims adjuster',
  ADMIN: 'Administrator',
};

export function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('') || '?';
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    <span aria-hidden="true" className={`inline-flex shrink-0 items-center justify-center rounded-full bg-accent-soft
      font-semibold text-accent-strong ${size === 'sm' ? 'h-6 w-6 text-[10px]' : 'h-8 w-8 text-xs'}`}>
      {initials(name)}
    </span>
  );
}

const navLinkClass = ({ isActive }: { isActive: boolean }) => `flex items-center gap-2.5 rounded-md px-2.5 py-1.5
  text-sm transition-colors ${isActive ? 'bg-hover font-medium text-ink' : 'text-ink-muted hover:bg-hover hover:text-ink'}`;

/** Workspace layout: role-based sidebar (a drawer on small screens) and a centred page column. */
export function AppShell() {
  const me = useCurrentUser();
  const logout = useLogout();
  const navigate = useNavigate();
  const user = me.data;
  const [open, setOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const sidebar = useRef<HTMLElement>(null);

  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!open) return;
    sidebar.current?.querySelector<HTMLElement>('a, button')?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        menuButton.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  const signOut = () => {
    logout.mutate(undefined, { onSettled: () => navigate('/', { replace: true }) });
  };

  return (
    <div className="min-h-screen bg-canvas lg:flex">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50
        focus:rounded-md focus:bg-surface focus:px-3 focus:py-2 focus:shadow-pop">Skip to content</a>

      {/* Small-screen top bar */}
      <div className="sticky top-0 z-30 flex items-center gap-2 border-b border-line bg-canvas/95 px-3 py-2
        backdrop-blur lg:hidden">
        <button ref={menuButton} type="button" onClick={() => setOpen(true)} aria-expanded={open}
          aria-controls="app-sidebar" data-testid="open-menu"
          className="inline-flex h-9 w-9 items-center justify-center rounded-md text-ink-muted hover:bg-hover">
          <Icon name="menu" size={20} />
          <span className="sr-only">Open navigation</span>
        </button>
        <Link to={user ? (user.role === 'POLICYHOLDER' ? '/claims' : '/staff/queue') : '/'} className="rounded-md">
          <Wordmark />
        </Link>
      </div>

      {open && (
        <button type="button" aria-label="Close navigation" onClick={close} tabIndex={-1}
          className="fixed inset-0 z-40 bg-ink/25 animate-fade-in lg:hidden" />
      )}

      <aside id="app-sidebar" ref={sidebar} aria-label="Workspace"
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-line bg-sidebar transition-transform
          duration-200 lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:w-60 lg:translate-x-0 lg:transition-none
          ${open ? 'translate-x-0 shadow-pop' : '-translate-x-full max-lg:invisible'}`}>
        <div className="flex items-center justify-between gap-2 px-3 pb-2 pt-3">
          <Link to="/" className="flex min-w-0 items-center gap-2 rounded-md px-1.5 py-1 hover:bg-hover"
            onClick={close} data-testid="sidebar-home">
            <WillowMark size={24} />
            <span className="truncate text-[15px] font-semibold text-ink">ClaimWillow</span>
          </Link>
          <button type="button" onClick={() => { close(); menuButton.current?.focus(); }}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-ink-faint hover:bg-hover lg:hidden">
            <Icon name="close" size={18} />
            <span className="sr-only">Close navigation</span>
          </button>
        </div>

        {user && (
          <nav aria-label="Primary" className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-2 py-2">
            <p className="px-2.5 pb-1 pt-2 text-xs font-medium text-ink-faint">
              {user.role === 'POLICYHOLDER' ? 'My home contents' : 'Claims operations'}
            </p>
            {navItemsFor(user).map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === '/claims'} data-testid={item.testId}
                className={navLinkClass} onClick={close}>
                <Icon name={item.icon} size={17} className="text-ink-faint" />
                {item.label}
              </NavLink>
            ))}
            {user.role === 'POLICYHOLDER' && (
              <NavLink to="/claims/new" className={navLinkClass} onClick={close} data-testid="nav-new-claim">
                <Icon name="plus" size={17} className="text-ink-faint" />
                New claim
              </NavLink>
            )}
            <p className="px-2.5 pb-1 pt-5 text-xs font-medium text-ink-faint">About</p>
            <Link to="/" className={navLinkClass({ isActive: false })} onClick={close}>
              <Icon name="home" size={17} className="text-ink-faint" />
              Product overview
            </Link>
          </nav>
        )}

        {user && (
          <div className="border-t border-line p-2">
            <div className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
              <Avatar name={user.displayName} />
              <div className="min-w-0 leading-tight">
                <p className="truncate text-sm font-medium text-ink" data-testid="current-user-name">{user.displayName}</p>
                <p className="truncate text-xs text-ink-faint">{roleLabel[user.role]}</p>
              </div>
            </div>
            <button type="button" onClick={signOut} data-testid="sign-out"
              className="mt-1 flex w-full items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm text-ink-muted
                hover:bg-hover hover:text-ink">
              <Icon name="logout" size={17} className="text-ink-faint" />
              Sign out
            </button>
            <p className="mt-2 px-2.5 pb-1 text-[11px] leading-snug text-ink-faint">
              Synthetic data only. Not a licensed insurance platform.
            </p>
          </div>
        )}
      </aside>

      <div className="min-w-0 flex-1">
        <main id="main" tabIndex={-1} className="mx-auto w-full max-w-[1080px] px-4 pb-16 pt-6 outline-none sm:px-8
          lg:px-12 lg:pt-12">
          <Outlet />
        </main>
        <footer className="mx-auto w-full max-w-[1080px] px-4 pb-8 text-xs text-ink-faint sm:px-8 lg:px-12">
          Synthetic data only. ClaimWillow is a portfolio application, not a licensed insurance platform.
          Do not enter real personal or insurance information.
        </footer>
      </div>
    </div>
  );
}
