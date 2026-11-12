import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Avatar, WillowMark } from '../components/AppShell';
import { Icon } from '../components/Icon';
import type { IconName } from '../components/Icon';
import { Pill } from '../components/StatusPill';
import type { Tone } from '../components/StatusPill';
import { buttonClass, Callout } from '../components/ui';
import { DEMO_PERSONAS, useDemoSignIn } from '../features/auth/demo';
import type { DemoPersona } from '../features/auth/demo';
import { homePathFor, useCurrentUser } from '../features/auth/useAuth';
import { useReducedMotion, useScrollReveal } from '../lib/motion';

export const REPOSITORY_URL = 'https://github.com/ved-cmpt/ClaimWillow';

const delay = (ms: number) => ({ '--reveal-delay': `${ms}ms` }) as CSSProperties;

/* ---------------------------------------------------------------- content */

const FLOW: { icon: IconName; title: string; text: string }[] = [
  { icon: 'file', title: 'Submit', text: 'The policyholder describes what was damaged or lost, adds an estimate and evidence.' },
  { icon: 'checkCircle', title: 'Automated checks', text: 'Six deterministic rules run at once and explain what an adjuster should look at.' },
  { icon: 'scale', title: 'Adjuster review', text: 'An adjuster takes the claim, reads the findings and can ask for more information.' },
  { icon: 'check', title: 'Decision', text: 'Approve or reject, always with a written note the policyholder can read.' },
  { icon: 'lock', title: 'Closed', text: 'The claim is closed. Its full history stays, and can never be edited.' },
];

const RULES: { code: string; title: string; text: string; blocking?: boolean }[] = [
  { code: 'R1', title: 'Required inputs', blocking: true,
    text: 'A policy you hold, an incident date that is not in the future, a type, a title, a description and an estimate of at least 0.01 CAD.' },
  { code: 'R2', title: 'Policy active on incident date',
    text: 'The incident must fall inside the policy period. If it does not, an adjuster reviews coverage.' },
  { code: 'R3', title: 'Estimate vs coverage limit',
    text: 'Flags an estimate above the coverage limit copied from the policy when the claim was submitted.' },
  { code: 'R4', title: 'Deductible',
    text: 'Flags an estimate at or below the deductible, as context for the review. No payout is calculated.' },
  { code: 'R5', title: 'Potential duplicate',
    text: "Compares the policyholder's own claims: same policy and type, nearby dates, similar amounts. Never a fraud verdict." },
  { code: 'R6', title: 'Completeness',
    text: 'Recommends more detail when the description is short or no evidence is attached.' },
];

const TRUST: { icon: IconName; title: string; points: string[] }[] = [
  { icon: 'history', title: 'Append-only audit trail', points: [
    'Status changes, notes, evidence and rule runs are recorded in the same transaction as the change.',
    'Database triggers reject any update or delete of a recorded event.',
    'Every claim keeps a snapshot of the policy terms it was submitted under.',
  ] },
  { icon: 'lock', title: 'Security by default', points: [
    'Server-side sessions, CSRF tokens on every change, BCrypt password hashes and login throttling.',
    'Every permission is checked by the server; other people’s claims answer "not found".',
    'Evidence is private, size-limited and checked by its file signature, not its name.',
  ] },
  { icon: 'beaker', title: 'Tested end to end', points: [
    'Unit tests for the rules and the state machine; integration tests against a real PostgreSQL.',
    'Component tests for forms, permissions and this page.',
    'Ten Cucumber scenarios drive a real browser through the whole claim lifecycle in CI.',
  ] },
];

const STACK = ['Java 21', 'Spring Boot', 'Spring Security', 'PostgreSQL', 'Flyway', 'React', 'TypeScript', 'Vite',
  'Tailwind CSS', 'TanStack Query', 'JUnit', 'Testcontainers', 'Vitest', 'Cucumber', 'Selenium', 'Docker',
  'GitHub Actions'];

/* ------------------------------------------------------- hero timeline */

const STORY: { actor: string; text: string; status: string; tone: Tone }[] = [
  { actor: 'Priya Shah', text: 'submitted the claim', status: 'Submitted', tone: 'info' },
  { actor: 'System', text: 'ran 6 checks · R6 suggests adding a photo', status: 'Submitted', tone: 'info' },
  { actor: 'Dana Okafor', text: 'assigned the claim and started the review', status: 'In review', tone: 'accent' },
  { actor: 'Dana Okafor', text: 'approved it: "Invoice and photos match."', status: 'Approved', tone: 'success' },
  { actor: 'Dana Okafor', text: 'closed the claim', status: 'Closed', tone: 'muted' },
];

const HOLD_TICKS = 3;

export function HeroTimeline() {
  const reduced = useReducedMotion();
  const [paused, setPaused] = useState(false);
  const [tick, setTick] = useState(0);
  const still = reduced || paused;

  useEffect(() => {
    if (still) return;
    const id = window.setInterval(() => setTick((t) => (t + 1) % (STORY.length + HOLD_TICKS)), 1700);
    return () => window.clearInterval(id);
  }, [still]);

  const step = still ? STORY.length - 1 : Math.min(tick, STORY.length - 1);
  const current = STORY[step];

  return (
    <figure className="relative mx-auto w-full max-w-md" data-testid="hero-timeline">
      <div aria-hidden="true" className="absolute -inset-4 -z-10 rounded-[20px] bg-linear-to-br from-accent-soft
        via-transparent to-tag-yellow/60 blur-2xl" />
      <div className="rounded-xl border border-line bg-surface p-5 shadow-pop">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-xs text-ink-faint">
            <Icon name="file" size={14} />Example claim · CW-2026-K7M4QX
          </div>
          <span key={current.status} className="animate-fade-in">
            <Pill tone={current.tone} label={current.status} />
          </span>
        </div>
        <p className="mt-2 text-base font-semibold text-ink">Burst pipe soaked the living-room sofa</p>
        <dl className="mt-3 grid grid-cols-2 gap-2 border-y border-line py-3 text-xs">
          <div><dt className="text-ink-faint">Estimate</dt><dd className="font-medium tabular text-ink">$1,800.00</dd></div>
          <div><dt className="text-ink-faint">Policy</dt><dd className="font-medium text-ink">HC-100421</dd></div>
        </dl>
        <ol aria-hidden="true" className="mt-4 flex flex-col gap-3">
          {STORY.map((s, i) => (
            <li key={s.text} className={`flex items-start gap-2.5 text-sm transition-all duration-500 ${i <= step
              ? 'translate-y-0 opacity-100' : 'translate-y-1 opacity-0'}`}>
              {s.actor === 'System'
                ? <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-subtle
                    text-ink-faint"><Icon name="sparkle" size={13} /></span>
                : <Avatar name={s.actor} size="sm" />}
              <p className="pt-0.5 leading-snug">
                <span className="font-medium text-ink">{s.actor}</span>{' '}
                <span className="text-ink-muted">{s.text}</span>
              </p>
              {i === step && !still && (
                <span className="pulse-dot ml-auto mt-2 h-2 w-2 shrink-0 rounded-full bg-accent" />
              )}
            </li>
          ))}
        </ol>
      </div>
      <figcaption className="mt-3 flex items-center justify-between gap-3 px-1 text-xs text-ink-faint">
        <span>An example claim moving from submitted to closed.</span>
        {!reduced && (
          <button type="button" onClick={() => setPaused((p) => !p)} aria-pressed={paused}
            className="rounded px-1.5 py-0.5 font-medium text-ink-muted hover:bg-hover hover:text-ink"
            data-testid="timeline-pause">
            {paused ? 'Play animation' : 'Pause animation'}
          </button>
        )}
      </figcaption>
    </figure>
  );
}

/* -------------------------------------------------------------- page */

function Section({ id, eyebrow, title, intro, children }: { id: string; eyebrow: string; title: string;
  intro?: ReactNode; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="scroll-mt-20 py-16 sm:py-24">
      <div className="reveal max-w-2xl">
        <p className="text-sm font-medium text-accent">{eyebrow}</p>
        <h2 id={`${id}-title`} className="mt-2 font-serif text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
          {title}
        </h2>
        {intro && <p className="mt-3 text-[17px] leading-relaxed text-ink-muted">{intro}</p>}
      </div>
      <div className="mt-10">{children}</div>
    </section>
  );
}

function DemoButtons({ testIdSuffix = '' }: { testIdSuffix?: string }) {
  const demo = useDemoSignIn();
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        {DEMO_PERSONAS.map((p: DemoPersona, i) => (
          <button key={p.key} type="button" data-testid={p.testId + testIdSuffix} onClick={() => demo.start(p)}
            disabled={demo.pending !== null} aria-busy={demo.pending === p.key || undefined}
            className={buttonClass(i === 0 ? 'primary' : 'secondary', 'px-4 py-2')}>
            {demo.pending === p.key
              ? <span aria-hidden="true" className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current
                  border-t-transparent" />
              : <Icon name={p.key === 'policyholder' ? 'home' : p.key === 'adjuster' ? 'scale' : 'users'} size={15} />}
            {p.label}
          </button>
        ))}
      </div>
      <div aria-live="polite" className="min-h-0">
        {demo.slow && (
          <p className="flex items-center gap-2 text-sm text-ink-muted" data-testid={`demo-waking${testIdSuffix}`}>
            <Icon name="clock" size={15} className="text-accent" />
            Waking the demo server. The first load can take up to a minute.
          </p>
        )}
        {demo.error && (
          <Callout tone="error" role="alert" testId={`demo-error${testIdSuffix}`} title="The demo sign-in did not work">
            {demo.error}
          </Callout>
        )}
      </div>
    </div>
  );
}

export function LandingPage() {
  const me = useCurrentUser();
  const user = me.data;
  const root = useRef<HTMLDivElement>(null);
  useScrollReveal(root);

  return (
    <div ref={root} className="min-h-screen bg-canvas" data-testid="landing">
      <a href="#landing-main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50
        focus:rounded-md focus:bg-surface focus:px-3 focus:py-2 focus:shadow-pop">Skip to content</a>

      <header className="sticky top-0 z-30 border-b border-line/80 bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <Link to="/" className="flex items-center gap-2 rounded-md font-semibold text-ink">
            <WillowMark size={26} /><span className="text-[17px] tracking-tight">ClaimWillow</span>
          </Link>
          <nav aria-label="Page sections" className="hidden items-center gap-1 text-sm text-ink-muted md:flex">
            {[['#how-it-works', 'How it works'], ['#rules', 'Rules'], ['#trust', 'Trust'], ['#stack', 'Stack']]
              .map(([href, label]) => (
                <a key={href} href={href} className="rounded-md px-2.5 py-1.5 hover:bg-hover hover:text-ink">{label}</a>
              ))}
          </nav>
          <div className="flex items-center gap-2">
            {user ? (
              <Link to={homePathFor(user)} data-testid="open-app" className={buttonClass('primary')}>
                Open app<Icon name="arrowRight" size={15} />
              </Link>
            ) : (
              <>
                <Link to="/login" data-testid="landing-sign-in" className={buttonClass('ghost')}>Sign in</Link>
                <a href="#demo" className={buttonClass('primary', 'hidden sm:inline-flex')}>Try the demo</a>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="landing-main" tabIndex={-1} className="outline-none">
        {/* Hero */}
        <div className="relative overflow-hidden border-b border-line">
          <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,
            rgb(14_107_97/0.07),transparent_55%)]" />
          <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 pb-16 pt-12 sm:px-6 sm:pb-24 sm:pt-20
            lg:grid-cols-[1.1fr_1fr]">
            <div id="demo" className="scroll-mt-24">
              <p className="inline-flex animate-fade-up items-center gap-2 rounded-full border border-line bg-surface
                px-3 py-1 text-xs font-medium text-ink-muted shadow-soft">
                <span className="h-1.5 w-1.5 rounded-full bg-accent" />
                Home-contents claims · synthetic data only
              </p>
              <h1 className="mt-5 animate-fade-up font-serif text-4xl font-semibold leading-[1.1] tracking-tight
                text-ink [animation-delay:80ms] sm:text-5xl lg:text-[3.4rem]">
                Claims for damaged or lost belongings, handled calmly and clearly.
              </h1>
              <p className="mt-5 max-w-xl animate-fade-up text-lg leading-relaxed text-ink-muted [animation-delay:160ms]">
                ClaimWillow takes a home-contents claim from first report to a closed decision: clear intake,
                automated checks that explain themselves, adjuster review and a history of every step that
                cannot be rewritten.
              </p>
              <div className="mt-8 animate-fade-up [animation-delay:240ms]">
                {user && (
                  <p className="mb-3 text-sm text-ink-muted" data-testid="landing-signed-in">
                    You are signed in as <span className="font-medium text-ink">{user.displayName}</span>.{' '}
                    <Link to={homePathFor(user)} className="font-medium text-accent underline underline-offset-2">
                      Open the app</Link> or switch to another demo account:
                  </p>
                )}
                <DemoButtons />
                <p className="mt-4 flex flex-wrap items-center gap-x-1.5 text-sm text-ink-faint">
                  <Icon name="clock" size={15} />
                  Runs on a free host, so the first load can take up to a minute.
                  <Link to="/login" className="font-medium text-ink-muted underline underline-offset-2 hover:text-ink">
                    Or sign in with a username.</Link>
                </p>
              </div>
            </div>
            <div className="animate-fade-up [animation-delay:200ms]">
              <HeroTimeline />
            </div>
          </div>
        </div>

        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          {/* Flow */}
          <Section id="how-it-works" eyebrow="How a claim flows" title="Five clear steps, one shared record."
            intro="Everyone sees the same claim page. Each step is allowed only from the right status, by the right person, and lands in the history.">
            <ol className="reveal relative grid gap-4 md:grid-cols-5 md:gap-3">
              <span aria-hidden="true" className="flow-line absolute left-[10%] right-[10%] top-6 hidden h-px
                bg-linear-to-r from-accent/50 via-accent/30 to-accent/50 md:block" />
              {FLOW.map((s, i) => (
                <li key={s.title} className="reveal relative flex gap-4 md:flex-col md:items-center md:text-center"
                  style={delay(120 + i * 110)}>
                  <span className="relative z-10 inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full
                    border border-line bg-surface text-accent shadow-soft">
                    <Icon name={s.icon} size={20} />
                  </span>
                  <div>
                    <p className="text-xs font-medium text-ink-faint">Step {i + 1}</p>
                    <h3 className="font-semibold text-ink">{s.title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-ink-muted">{s.text}</p>
                  </div>
                </li>
              ))}
            </ol>
          </Section>

          {/* Rules */}
          <Section id="rules" eyebrow="Automated checks" title="Six rules, each one explained."
            intro="No black boxes and no automatic rejections. Rules are deterministic, versioned, shown on every claim and re-run after each policyholder response.">
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {RULES.map((r, i) => (
                <li key={r.code} className="reveal rounded-card border border-line bg-surface p-5 shadow-soft
                  transition-shadow hover:shadow-pop" style={delay((i % 3) * 90)}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-md bg-accent-soft px-1.5
                      text-xs font-semibold text-accent-strong">{r.code}</span>
                    <Pill tone={r.blocking ? 'warning' : 'neutral'}
                      label={r.blocking ? 'Blocks submission' : 'Flags for review'} />
                  </div>
                  <h3 className="mt-3 font-semibold text-ink">{r.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-ink-muted">{r.text}</p>
                </li>
              ))}
            </ul>
          </Section>

          {/* Trust */}
          <Section id="trust" eyebrow="Built to be trusted" title="Audit trail, security and tests you can read."
            intro="The source, the tests and their latest results are public in the repository.">
            <div className="grid gap-3 lg:grid-cols-3">
              {TRUST.map((t, i) => (
                <article key={t.title} className="reveal rounded-card border border-line bg-surface p-6 shadow-soft"
                  style={delay(i * 100)}>
                  <span className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-accent-soft text-accent">
                    <Icon name={t.icon} size={20} />
                  </span>
                  <h3 className="mt-4 font-semibold text-ink">{t.title}</h3>
                  <ul className="mt-3 flex flex-col gap-2.5">
                    {t.points.map((p) => (
                      <li key={p} className="flex gap-2 text-sm leading-relaxed text-ink-muted">
                        <Icon name="check" size={15} className="mt-[3px] text-accent" />{p}
                      </li>
                    ))}
                  </ul>
                </article>
              ))}
            </div>
          </Section>

          {/* Stack */}
          <section id="stack" aria-labelledby="stack-title" className="reveal scroll-mt-20 border-t border-line py-14">
            <h2 id="stack-title" className="text-sm font-medium text-ink-faint">Built with</h2>
            <ul className="mt-4 flex flex-wrap gap-2">
              {STACK.map((s) => (
                <li key={s} className="rounded-md border border-line bg-surface px-2.5 py-1 text-sm text-ink-muted">{s}</li>
              ))}
            </ul>
          </section>

          {/* Closing call to action */}
          <section aria-labelledby="cta-title" className="reveal mb-16 rounded-xl border border-line bg-surface p-6
            shadow-soft sm:p-10">
            <h2 id="cta-title" className="font-serif text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
              See it from each side of the claim.
            </h2>
            <p className="mt-2 max-w-2xl text-ink-muted">
              Each demo signs you in to a shared, synthetic account. Data may be reset at any time.
            </p>
            <ul className="mt-5 grid gap-2 text-sm text-ink-muted sm:grid-cols-3">
              {DEMO_PERSONAS.map((p) => (
                <li key={p.key} className="flex gap-2"><Icon name="arrowRight" size={15} className="mt-[3px] text-accent" />
                  <span><span className="font-medium text-ink">{p.role}:</span> {p.summary}</span></li>
              ))}
            </ul>
            <div className="mt-6"><DemoButtons testIdSuffix="-footer" /></div>
          </section>
        </div>
      </main>

      <footer className="border-t border-line bg-sidebar">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 sm:px-6 md:flex-row md:justify-between">
          <div className="max-w-md">
            <div className="flex items-center gap-2 font-semibold text-ink"><WillowMark size={22} />ClaimWillow</div>
            <p className="mt-3 text-sm text-ink-muted" data-testid="synthetic-notice">
              <strong className="font-semibold text-ink">Synthetic data only.</strong> Every person, policy and claim
              is fictional. ClaimWillow is a portfolio application, not a licensed insurance platform. Do not enter
              real personal or insurance information.
            </p>
          </div>
          <nav aria-label="Footer" className="flex flex-col gap-2 text-sm">
            <a href={REPOSITORY_URL} className="inline-flex items-center gap-2 text-ink-muted hover:text-ink"
              data-testid="repo-link">
              <Icon name="code" size={15} />Source code on GitHub
            </a>
            <Link to="/login" className="inline-flex items-center gap-2 text-ink-muted hover:text-ink">
              <Icon name="user" size={15} />Sign in
            </Link>
            <span className="text-ink-faint">MIT licence · © 2026 Vedsai Vangapandu</span>
          </nav>
        </div>
      </footer>
    </div>
  );
}
