import { forwardRef, useId } from 'react';
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes,
  TextareaHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const variants: Record<Variant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-strong border border-accent',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-subtle',
  danger: 'bg-danger text-white border border-danger hover:opacity-90',
  ghost: 'bg-transparent text-accent border border-transparent hover:bg-accent-soft',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
}

export function Button({ variant = 'primary', busy = false, className = '', children, disabled, ...rest }:
  ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-medium
        transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
    >
      {busy && (
        <span aria-hidden="true"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  );
}

interface FieldShellProps {
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: (ids: { id: string; describedBy?: string; invalid: boolean }) => ReactNode;
}

export function FieldShell({ label, required, hint, error, children }: FieldShellProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
        {required ? <span className="text-danger"> *</span> : <span className="font-normal text-ink-faint"> (optional)</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && <p id={hintId} className="text-xs text-ink-faint">{hint}</p>}
      {error && <p id={errorId} className="text-sm text-danger">{error}</p>}
    </div>
  );
}

const inputClass = `w-full rounded-md border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint
  focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/25`;

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, required, className = '', ...rest }, ref) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <input ref={ref} id={id} aria-describedby={describedBy} aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={`${inputClass} ${invalid ? 'border-danger' : 'border-line-strong'} ${className}`} {...rest} />
      )}
    </FieldShell>
  );
});

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  hint?: string;
  error?: string;
}

export function TextArea({ label, hint, error, required, className = '', ...rest }: TextAreaProps) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <textarea id={id} aria-describedby={describedBy} aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={`${inputClass} min-h-28 ${invalid ? 'border-danger' : 'border-line-strong'} ${className}`}
          {...rest} />
      )}
    </FieldShell>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

export function SelectField({ label, hint, error, required, className = '', children, ...rest }: SelectFieldProps) {
  return (
    <FieldShell label={label} required={required} hint={hint} error={error}>
      {({ id, describedBy, invalid }) => (
        <select id={id} aria-describedby={describedBy} aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={`${inputClass} ${invalid ? 'border-danger' : 'border-line-strong'} ${className}`} {...rest}>
          {children}
        </select>
      )}
    </FieldShell>
  );
}

export function Card({ children, className = '', ...rest }: { children: ReactNode; className?: string } &
  React.HTMLAttributes<HTMLElement>) {
  return (
    <section className={`rounded-card border border-line bg-surface ${className}`} {...rest}>
      {children}
    </section>
  );
}

export function CardHeader({ title, action, id }: { title: string; action?: ReactNode; id?: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3">
      <h2 id={id} className="text-sm font-semibold uppercase tracking-wide text-ink-muted">{title}</h2>
      {action}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

type AlertTone = 'error' | 'warning' | 'info' | 'success';

const alertTones: Record<AlertTone, string> = {
  error: 'border-danger/30 bg-danger-soft text-danger',
  warning: 'border-warning/30 bg-warning-soft text-warning',
  info: 'border-info/30 bg-info-soft text-info',
  success: 'border-success/30 bg-success-soft text-success',
};

export function Alert({ tone = 'info', title, children, testId }: { tone?: AlertTone; title?: string;
  children?: ReactNode; testId?: string }) {
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} data-testid={testId}
      className={`rounded-md border px-4 py-3 text-sm ${alertTones[tone]}`}>
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={title ? 'mt-1' : ''}>{children}</div>}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <p className="text-base font-medium text-ink">{title}</p>
      {children && <p className="max-w-md text-sm text-ink-muted">{children}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-3 p-5" aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-5 animate-pulse rounded bg-subtle" style={{ width: `${90 - i * 12}%` }} />
      ))}
    </div>
  );
}
