import type { PolicyStatus } from '../api/types';

type Tone = 'neutral' | 'info' | 'accent' | 'warning' | 'success' | 'danger' | 'muted';

const tones: Record<Tone, string> = {
  neutral: 'bg-subtle text-ink-muted border-line-strong',
  info: 'bg-info-soft text-info border-info/30',
  accent: 'bg-accent-soft text-accent-strong border-accent/30',
  warning: 'bg-warning-soft text-warning border-warning/30',
  success: 'bg-success-soft text-success border-success/30',
  danger: 'bg-danger-soft text-danger border-danger/30',
  muted: 'bg-surface text-ink-faint border-line',
};

/** Status pill: always shows the text label (never color alone) plus a shape marker. */
export function Pill({ tone, label, testId }: { tone: Tone; label: string; testId?: string }) {
  return (
    <span data-testid={testId}
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs
        font-medium ${tones[tone]}`}>
      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}

const policyTones: Record<PolicyStatus, Tone> = { ACTIVE: 'success', EXPIRED: 'muted', CANCELLED: 'danger' };
const policyLabels: Record<PolicyStatus, string> = { ACTIVE: 'Active', EXPIRED: 'Expired', CANCELLED: 'Cancelled' };

export function PolicyStatusPill({ status }: { status: PolicyStatus }) {
  return <Pill tone={policyTones[status]} label={policyLabels[status]} />;
}
