import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { Icon } from './Icon';
import type { IconName } from './Icon';

const chip = `group inline-flex h-8 items-center gap-1.5 rounded-md border border-line-strong bg-surface pl-2.5 text-sm
  shadow-soft hover:border-ink-faint focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/20`;

interface FilterSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  icon?: IconName;
  children: ReactNode;
}

/** Filter control with its visible label inside the chip (the label element wraps the select). */
export function FilterSelect({ label, icon, children, className = '', ...rest }: FilterSelectProps) {
  return (
    <label className={`${chip} ${className}`}>
      {icon && <Icon name={icon} size={14} className="text-ink-faint" />}
      <span className="whitespace-nowrap text-ink-faint">{label}</span>
      <select aria-label={label} {...rest} className="h-full min-w-0 cursor-pointer rounded-r-md bg-transparent pl-0.5 pr-2 font-medium
        text-ink focus:outline-none">
        {children}
      </select>
    </label>
  );
}

interface FilterDateProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Full accessible name when the visible label is shortened. */
  accessibleLabel?: string;
}

export function FilterDate({ label, accessibleLabel, className = '', ...rest }: FilterDateProps) {
  return (
    <label className={`${chip} pr-1 ${className}`}>
      <Icon name="calendar" size={14} className="text-ink-faint" />
      <span className="whitespace-nowrap text-ink-faint">{label}</span>
      <input type="date" aria-label={accessibleLabel ?? label} {...rest} className="h-full min-w-0 bg-transparent pr-1 font-medium text-ink
        focus:outline-none" />
    </label>
  );
}

interface SearchInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
}

/** Search box with an accessible (visually hidden) label and a leading icon. */
export function SearchInput({ label, id, className = '', ...rest }: SearchInputProps) {
  return (
    <div className={`relative min-w-0 ${className}`}>
      <label htmlFor={id} className="sr-only">{label}</label>
      <Icon name="search" size={15} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2
        text-ink-faint" />
      <input id={id} type="search" {...rest} className="h-8 w-full rounded-md border border-line-strong bg-surface
        pl-8 pr-2.5 text-sm text-ink shadow-soft placeholder:text-ink-faint hover:border-ink-faint focus:border-accent
        focus:outline-none focus:ring-2 focus:ring-accent/20" />
    </div>
  );
}

/** Frame for a database-style table: optional toolbar on top, rows, optional footer. */
export function TableFrame({ toolbar, children, footer }: { toolbar?: ReactNode; children: ReactNode;
  footer?: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-card border border-line bg-surface shadow-soft">
      {toolbar && <div className="border-b border-line bg-canvas/60 px-3 py-2.5 sm:px-4">{toolbar}</div>}
      {children}
      {footer}
    </section>
  );
}

export const theadClass = 'border-b border-line text-left';
export const rowClass = 'transition-colors hover:bg-subtle';
