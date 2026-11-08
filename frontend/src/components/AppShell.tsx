import { Outlet } from 'react-router-dom';

export function Wordmark() {
  return (
    <span className="flex items-center gap-2 font-semibold tracking-tight text-ink">
      <svg aria-hidden="true" viewBox="0 0 32 32" className="h-7 w-7">
        <rect width="32" height="32" rx="7" fill="#0f5f5c" />
        <path d="M9 22c3-9 7-12 14-13-4 3-6 7-7 13" fill="none" stroke="#f7f5f0" strokeWidth="2.4"
          strokeLinecap="round" />
      </svg>
      <span className="text-lg">ClaimWillow</span>
    </span>
  );
}

export function AppShell() {
  return (
    <div className="min-h-screen bg-canvas">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <Wordmark />
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
      <footer className="mx-auto max-w-6xl px-4 pb-8 text-xs text-ink-faint">
        Synthetic data only. ClaimWillow is a portfolio application, not a licensed insurance platform.
      </footer>
    </div>
  );
}
