import { forwardRef, useId } from 'react';
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes,
  TextareaHTMLAttributes } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from './Icon';
import type { IconName } from './Icon';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

const variants: Record<Variant, string> = {
  primary: 'bg-accent text-white border border-accent hover:bg-accent-strong hover:border-accent-strong shadow-soft',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-subtle shadow-soft',
  danger: 'bg-danger text-white border border-danger hover:opacity-90 shadow-soft',
  ghost: 'bg-transparent text-ink-muted border border-transparent hover:bg-hover hover:text-ink',
};

export const buttonBase = `inline-flex items-center justify-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium
  transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60`;

export function buttonClass(variant: Variant = 'primary', extra = ''): string {
  return `${buttonBase} ${variants[variant]} ${extra}`;
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  busy?: boolean;
  icon?: IconName;
}

export function Button({ variant = 'primary', busy = false, icon, className = '', children, disabled, ...rest }:
  ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={buttonClass(variant, className)}
    >
      {busy ? (
        <span aria-hidden="true"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : icon && <Icon name={icon} size={15} />}
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
      {hint && <p id={hintId} className="text-xs leading-relaxed text-ink-faint">{hint}</p>}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-sm text-danger">
          <Icon name="alert" size={14} className="mt-[3px]" />{error}
        </p>
      )}
    </div>
  );
}

export const inputClass = `w-full rounded-md border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint
  shadow-[inset_0_1px_1px_rgb(42_41_37/0.03)] transition-colors focus:border-accent focus:outline-none focus:ring-2
  focus:ring-accent/20`;

/** Compact control used in filter bars. */
export const controlClass = `rounded-md border border-line-strong bg-surface px-2.5 py-1.5 text-sm text-ink
  hover:border-ink-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20`;

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
          className={`${inputClass} min-h-28 leading-relaxed ${invalid ? 'border-danger' : 'border-line-strong'} ${className}`}
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

/** A bordered content block on the page. */
export function Card({ children, className = '', ...rest }: { children: ReactNode; className?: string } &
  HTMLAttributes<HTMLElement>) {
  return (
    <section className={`rounded-card border border-line bg-surface shadow-soft ${className}`} {...rest}>
      {children}
    </section>
  );
}

export function CardHeader({ title, action, id, icon }: { title: string; action?: ReactNode; id?: string;
  icon?: IconName }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-2.5 sm:px-5">
      <h2 id={id} className="flex items-center gap-2 text-[15px] font-semibold text-ink">
        {icon && <Icon name={icon} size={16} className="text-ink-faint" />}
        {title}
      </h2>
      {action}
    </div>
  );
}

/** Heading for an unboxed page section, like a heading block in a document. */
export function BlockHeading({ title, icon, id, action }: { title: string; icon?: IconName; id?: string;
  action?: ReactNode }) {
  return (
    <div className="mb-2 flex items-center justify-between gap-3">
      <h2 id={id} className="flex items-center gap-2 text-base font-semibold text-ink">
        {icon && <Icon name={icon} size={17} className="text-ink-faint" />}
        {title}
      </h2>
      {action}
    </div>
  );
}

export interface Crumb {
  label: string;
  to?: string;
}

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className="mb-4 text-sm text-ink-faint" data-testid="breadcrumbs">
      <ol className="flex flex-wrap items-center gap-1">
        {items.map((c, i) => (
          <li key={`${c.label}-${i}`} className="flex items-center gap-1">
            {i > 0 && <Icon name="chevronRight" size={13} className="text-line-strong" />}
            {c.to && i < items.length - 1
              ? <Link to={c.to} className="rounded px-1 py-0.5 hover:bg-hover hover:text-ink">{c.label}</Link>
              : <span aria-current={i === items.length - 1 ? 'page' : undefined} className="px-1 py-0.5 text-ink-muted">
                  {c.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function PageIcon({ name }: { name: IconName }) {
  return (
    <span aria-hidden="true" className="mb-3 inline-flex h-11 w-11 items-center justify-center rounded-lg border
      border-line bg-surface text-accent shadow-soft">
      <Icon name={name} size={22} />
    </span>
  );
}

export function PageHeader({ title, subtitle, actions, icon, breadcrumbs }: { title: string; subtitle?: ReactNode;
  actions?: ReactNode; icon?: IconName; breadcrumbs?: Crumb[] }) {
  return (
    <header className="mb-8">
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          {icon && <PageIcon name={icon} />}
          <h1 className="text-[1.75rem] font-bold leading-tight tracking-tight text-ink sm:text-[2rem]">{title}</h1>
          {subtitle && <p className="mt-1.5 text-[15px] text-ink-muted">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </header>
  );
}

type CalloutTone = 'error' | 'warning' | 'info' | 'success' | 'neutral' | 'accent';

const calloutTones: Record<CalloutTone, { box: string; icon: IconName; iconClass: string }> = {
  error: { box: 'bg-danger-soft border-danger/15', icon: 'alert', iconClass: 'text-danger' },
  warning: { box: 'bg-warning-soft border-warning/15', icon: 'alert', iconClass: 'text-warning' },
  info: { box: 'bg-info-soft border-info/15', icon: 'info', iconClass: 'text-info' },
  success: { box: 'bg-success-soft border-success/15', icon: 'checkCircle', iconClass: 'text-success' },
  neutral: { box: 'bg-subtle border-line', icon: 'lightbulb', iconClass: 'text-ink-muted' },
  accent: { box: 'bg-accent-soft border-accent/15', icon: 'info', iconClass: 'text-accent' },
};

/** Callout block: an icon, an optional title and body text on a soft tinted background. */
export function Callout({ tone = 'neutral', title, children, icon, testId, role, className = '', ...rest }: {
  tone?: CalloutTone; title?: ReactNode; children?: ReactNode; icon?: IconName; testId?: string; role?: string;
  className?: string } & Omit<HTMLAttributes<HTMLDivElement>, 'title' | 'role'>) {
  const t = calloutTones[tone];
  return (
    <div role={role} data-testid={testId} {...rest}
      className={`flex gap-3 rounded-md border px-4 py-3 text-sm text-ink ${t.box} ${className}`}>
      <Icon name={icon ?? t.icon} size={18} className={`mt-[1px] ${t.iconClass}`} />
      <div className="min-w-0 flex-1">
        {title && <div className="font-semibold">{title}</div>}
        {children && <div className={`${title ? 'mt-0.5' : ''} text-ink-muted`}>{children}</div>}
      </div>
    </div>
  );
}

type AlertTone = 'error' | 'warning' | 'info' | 'success';

/** Status message rendered as a callout; errors are announced assertively. */
export function Alert({ tone = 'info', title, children, testId }: { tone?: AlertTone; title?: string;
  children?: ReactNode; testId?: string }) {
  return (
    <Callout tone={tone} title={title} testId={testId} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </Callout>
  );
}

export interface Property {
  label: string;
  icon: IconName;
  value: ReactNode;
  testId?: string;
}

/** Property list: label column with icons on the left, values on the right, like page properties. */
export function PropertyList({ items, className = '', ...rest }: { items: Property[]; className?: string } &
  HTMLAttributes<HTMLDListElement>) {
  return (
    <dl className={`flex flex-col ${className}`} {...rest}>
      {items.map((p) => (
        <div key={p.label} className="grid grid-cols-[minmax(8rem,10rem)_1fr] items-start gap-3 rounded-md px-1.5
          py-1.5 text-sm hover:bg-subtle">
          <dt className="flex items-center gap-2 text-ink-faint">
            <Icon name={p.icon} size={15} />
            {p.label}
          </dt>
          <dd className="min-w-0 break-words text-ink" data-testid={p.testId}>{p.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Toggle block: a disclosure that is keyboard-operable and announces its state natively. */
export function Toggle({ summary, children, className = '', testId, defaultOpen = false }: { summary: ReactNode;
  children: ReactNode; className?: string; testId?: string; defaultOpen?: boolean }) {
  return (
    <details className={`toggle group ${className}`} data-testid={testId} open={defaultOpen || undefined}>
      <summary className="flex cursor-pointer select-none items-center gap-1.5 rounded-md px-1.5 py-1 text-sm
        font-medium text-ink hover:bg-hover">
        <Icon name="chevronRight" size={14} className="toggle-caret text-ink-faint" />
        {summary}
      </summary>
      <div className="pb-1 pl-7 pr-2 pt-1.5">{children}</div>
    </details>
  );
}

export function EmptyState({ title, children, action, icon = 'folder' }: { title: string; children?: ReactNode;
  action?: ReactNode; icon?: IconName }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-14 text-center">
      <span aria-hidden="true" className="mb-1 inline-flex h-10 w-10 items-center justify-center rounded-full
        bg-subtle text-ink-faint">
        <Icon name={icon} size={20} />
      </span>
      <p className="text-base font-semibold text-ink">{title}</p>
      {children && <p className="max-w-md text-sm text-ink-muted">{children}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-3 p-5" aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-4 animate-pulse rounded bg-subtle" style={{ width: `${90 - i * 12}%` }} />
      ))}
    </div>
  );
}

/** Database-style table header cell. */
export function Th({ children, className = '', icon }: { children?: ReactNode; className?: string; icon?: IconName }) {
  return (
    <th scope="col" className={`px-3 py-2 text-xs font-medium text-ink-faint ${className}`}>
      <span className="inline-flex items-center gap-1.5">
        {icon && <Icon name={icon} size={13} />}
        {children}
      </span>
    </th>
  );
}
