import type { PolicyStatus } from '../api/types';

export type Tone = 'neutral' | 'info' | 'accent' | 'warning' | 'success' | 'danger' | 'muted' | 'yellow';

const tones: Record<Tone, string> = {
  neutral: 'bg-tag-gray text-tag-gray-ink',
  info: 'bg-tag-blue text-tag-blue-ink',
  accent: 'bg-tag-purple text-tag-purple-ink',
  warning: 'bg-tag-orange text-tag-orange-ink',
  yellow: 'bg-tag-yellow text-tag-yellow-ink',
  success: 'bg-tag-green text-tag-green-ink',
  danger: 'bg-tag-red text-tag-red-ink',
  muted: 'bg-tag-brown text-tag-brown-ink',
};

/** Soft coloured tag: always shows the text label (never colour alone) plus a dot marker. */
export function Pill({ tone, label, testId }: { tone: Tone; label: string; testId?: string }) {
  return (
    <span data-testid={testId}
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-[4px] px-1.5 py-[1px] text-[13px]
        font-medium leading-5 ${tones[tone]}`}>
      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {label}
    </span>
  );
}

const policyTones: Record<PolicyStatus, Tone> = { ACTIVE: 'success', EXPIRED: 'neutral', CANCELLED: 'danger' };
const policyLabels: Record<PolicyStatus, string> = { ACTIVE: 'Active', EXPIRED: 'Expired', CANCELLED: 'Cancelled' };

export function PolicyStatusPill({ status }: { status: PolicyStatus }) {
  return <Pill tone={policyTones[status]} label={policyLabels[status]} />;
}
