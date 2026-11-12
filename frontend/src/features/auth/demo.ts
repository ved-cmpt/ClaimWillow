import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiRequestError } from '../../api/client';
import { homePathFor, useLogin } from './useAuth';

/** Public synthetic demo accounts; they all share the published demo password. */
export const DEMO_ACCOUNTS = [
  { username: 'priya', role: 'Policyholder' },
  { username: 'marcus', role: 'Policyholder' },
  { username: 'dana', role: 'Adjuster' },
  { username: 'sam', role: 'Adjuster' },
  { username: 'admin', role: 'Admin' },
] as const;
export const DEMO_PASSWORD = 'demo1234';

export interface DemoPersona {
  key: 'policyholder' | 'adjuster' | 'admin';
  username: string;
  role: string;
  label: string;
  testId: string;
  summary: string;
}

/** The three one-click demos offered on the landing page. */
export const DEMO_PERSONAS: DemoPersona[] = [
  { key: 'policyholder', username: 'priya', role: 'Policyholder', label: 'Try as a policyholder (Priya)', testId: 'demo-policyholder',
    summary: 'Start a claim, answer an adjuster and follow every status change.' },
  { key: 'adjuster', username: 'dana', role: 'Adjuster', label: 'Try as an adjuster (Dana)', testId: 'demo-adjuster',
    summary: 'Work the queue: assign, review rule findings, request information, decide.' },
  { key: 'admin', username: 'admin', role: 'Admin', label: 'Try as an admin', testId: 'demo-admin',
    summary: 'Take over claims, close decided ones, export reports, manage accounts.' },
];

/** After this long without a response we explain the free host's cold start. */
export const SLOW_AFTER_MS = 4000;

/**
 * Signs in with a public demo account through the normal login endpoint (the API client adds
 * the CSRF header), then opens that role's home screen.
 */
export function useDemoSignIn() {
  const login = useLogin();
  const navigate = useNavigate();
  const [pending, setPending] = useState<DemoPersona['key'] | null>(null);
  const [slow, setSlow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const start = (persona: DemoPersona) => {
    if (pending) return;
    setPending(persona.key);
    setError(null);
    setSlow(false);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setSlow(true), SLOW_AFTER_MS);
    login.mutate({ username: persona.username, password: DEMO_PASSWORD }, {
      onSuccess: (user) => {
        window.clearTimeout(timer.current);
        navigate(homePathFor(user));
      },
      onError: (err) => setError(err instanceof ApiRequestError
        ? `${err.message} You can also sign in with the form on the sign-in page.`
        : 'The demo server could not be reached. It may still be waking up; try again in a moment.'),
      onSettled: () => {
        window.clearTimeout(timer.current);
        setPending(null);
        setSlow(false);
      },
    });
  };

  return { start, pending, slow, error };
}
