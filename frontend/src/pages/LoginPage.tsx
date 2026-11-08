import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { Wordmark } from '../components/AppShell';
import { Alert, Button, TextField } from '../components/ui';
import { Spinner } from '../components/Spinner';
import { homePathFor, useCurrentUser, useLogin } from '../features/auth/useAuth';

interface FormErrors {
  email?: string;
  password?: string;
}

export function validateLogin(email: string, password: string): FormErrors {
  const errors: FormErrors = {};
  if (!email.trim()) errors.email = 'Email is required.';
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) errors.email = 'Enter a valid email address.';
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
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});

  if (me.isPending) {
    return <div className="flex min-h-screen items-center justify-center"><Spinner label="Loading" /></div>;
  }
  if (me.data) {
    return <Navigate to={safeNext(params.get('next')) ?? homePathFor(me.data)} replace />;
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const found = validateLogin(email, password);
    setErrors(found);
    if (found.email || found.password) return;
    login.mutate({ email: email.trim(), password }, {
      onSuccess: (user) => navigate(safeNext(params.get('next')) ?? homePathFor(user), { replace: true }),
    });
  };

  const serverError = login.error instanceof ApiRequestError ? login.error.message
    : login.error ? 'We could not sign you in. Try again.' : null;

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <div className="mx-auto grid w-full max-w-5xl flex-1 items-center gap-10 px-4 py-12 md:grid-cols-2">
        <div className="max-w-md">
          <Wordmark className="mb-6" />
          <h1 className="text-3xl font-semibold tracking-tight text-ink">Home contents claims, handled clearly.</h1>
          <p className="mt-4 text-ink-muted">
            Policyholders submit and follow claims for damaged or lost belongings. Adjusters triage, review
            rule findings and record decisions with a complete audit trail.
          </p>
          <p className="mt-6 text-xs text-ink-faint">
            Synthetic data only. This portfolio application does not process real insurance claims.
          </p>
        </div>
        <form noValidate onSubmit={onSubmit} aria-labelledby="signin-heading" data-testid="login-form"
          className="w-full max-w-md rounded-card border border-line bg-surface p-6 shadow-sm md:justify-self-end">
          <h2 id="signin-heading" className="text-lg font-semibold text-ink">Sign in</h2>
          <p className="mt-1 text-sm text-ink-muted">Use your ClaimWillow account.</p>
          <div className="mt-6 flex flex-col gap-4">
            {serverError && <Alert tone="error" testId="login-error">{serverError}</Alert>}
            <TextField label="Email" type="email" name="email" autoComplete="username" required
              value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email}
              data-testid="login-email" />
            <TextField label="Password" type="password" name="password" autoComplete="current-password" required
              value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password}
              data-testid="login-password" />
            <Button type="submit" busy={login.isPending} data-testid="login-submit" className="mt-2 w-full">
              Sign in
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
