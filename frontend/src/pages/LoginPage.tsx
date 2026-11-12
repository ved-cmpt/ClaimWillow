import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { WillowMark } from '../components/AppShell';
import { Icon } from '../components/Icon';
import { Alert, Button, TextField } from '../components/ui';
import { Spinner } from '../components/Spinner';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../features/auth/demo';
import { homePathFor, useCurrentUser, useLogin } from '../features/auth/useAuth';

export { DEMO_ACCOUNTS, DEMO_PASSWORD };

interface FormErrors {
  username?: string;
  password?: string;
}

export function validateLogin(username: string, password: string): FormErrors {
  const errors: FormErrors = {};
  if (!username.trim()) errors.username = 'Username is required.';
  if (!password) errors.password = 'Password is required.';
  return errors;
}

/** Only allow same-app relative redirects after sign-in. */
function safeNext(next: string | null): string | null {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/login')) return null;
  return next;
}

export function LoginPage() {
  const me = useCurrentUser();
  const login = useLogin();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});

  if (me.isPending) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-canvas px-4 text-center">
        <Spinner label="Loading" />
        <p className="max-w-xs text-xs text-ink-faint">The free demo host may take a minute or two to wake up.</p>
      </div>
    );
  }
  if (me.data) {
    return <Navigate to={safeNext(params.get('next')) ?? homePathFor(me.data)} replace />;
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const found = validateLogin(username, password);
    setErrors(found);
    if (found.username || found.password) return;
    login.mutate({ username: username.trim(), password }, {
      onSuccess: (user) => navigate(safeNext(params.get('next')) ?? homePathFor(user), { replace: true }),
    });
  };

  const fill = (name: string) => {
    setUsername(name);
    setPassword(DEMO_PASSWORD);
    setErrors({});
  };

  const serverError = login.error instanceof ApiRequestError ? login.error.message
    : login.error ? 'We could not sign you in. Try again.' : null;

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-4 sm:px-6">
        <Link to="/" className="flex items-center gap-2 rounded-md font-semibold text-ink">
          <WillowMark size={26} /><span className="text-[17px] tracking-tight">ClaimWillow</span>
        </Link>
        <Link to="/" className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-ink-muted
          hover:bg-hover hover:text-ink">
          <Icon name="chevronLeft" size={15} />Back to overview
        </Link>
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pb-16 pt-6 sm:pt-12">
        <div className="w-full max-w-sm animate-fade-up">
          <h1 className="text-center font-serif text-3xl font-semibold tracking-tight text-ink">Sign in</h1>
          <p className="mt-2 text-center text-sm text-ink-muted">Use a demo account below, or your own username.</p>
          <form noValidate onSubmit={onSubmit} aria-labelledby="signin-heading" data-testid="login-form"
            className="mt-8 rounded-xl border border-line bg-surface p-6 shadow-pop">
            <h2 id="signin-heading" className="sr-only">Sign in</h2>
            <div className="flex flex-col gap-4">
              {serverError && <Alert tone="error" testId="login-error">{serverError}</Alert>}
              <TextField label="Username" type="text" name="username" autoComplete="username" required
                autoCapitalize="none" autoCorrect="off" spellCheck={false} placeholder="e.g. priya"
                value={username} onChange={(e) => setUsername(e.target.value)} error={errors.username}
                data-testid="login-username" />
              <TextField label="Password" type="password" name="password" autoComplete="current-password" required
                value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password}
                data-testid="login-password" />
              <Button type="submit" busy={login.isPending} data-testid="login-submit" className="mt-1 w-full py-2">
                Sign in
              </Button>
              {login.isPending && (
                <p className="text-center text-xs text-ink-faint" aria-live="polite">
                  The first sign-in after a quiet spell can take a minute or two.
                </p>
              )}
            </div>
          </form>
          <section className="mt-4 rounded-lg border border-line bg-sidebar p-4 text-sm text-ink-muted"
            data-testid="demo-accounts" aria-labelledby="demo-accounts-title">
            <h2 id="demo-accounts-title" className="flex items-center gap-2 font-medium text-ink">
              <Icon name="lightbulb" size={15} className="text-accent" />Demo accounts
            </h2>
            <p className="mt-1">Choose one to fill in the form, then sign in.</p>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {DEMO_ACCOUNTS.map((a) => (
                <li key={a.username}>
                  <button type="button" onClick={() => fill(a.username)}
                    className="rounded-md border border-line bg-surface px-2 py-0.5 text-xs hover:border-ink-faint">
                    <code className="font-mono text-ink">{a.username}</code>
                    <span className="text-ink-faint"> ({a.role})</span>
                  </button>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-xs">
              Password for all: <code className="font-mono text-ink">{DEMO_PASSWORD}</code>. Synthetic data only.
            </p>
          </section>
        </div>
      </main>
    </div>
  );
}
