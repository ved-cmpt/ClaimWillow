const money = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

export function formatMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(n) ? money.format(n) : '—';
}

/**
 * Formats an ISO calendar date (YYYY-MM-DD) without time-zone conversion, so an incident
 * date never shifts by a day for users west of UTC.
 */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return isoDate;
  return new Date(y, m - 1, d).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Formats a UTC instant in the viewer's local time zone. */
export function formatDateTime(instant: string | null | undefined): string {
  if (!instant) return '—';
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return instant;
  return date.toLocaleString('en-CA', {
    year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}

/** Today's local calendar date as YYYY-MM-DD. */
export function todayIso(): string {
  const now = new Date();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${mm}-${dd}`;
}
