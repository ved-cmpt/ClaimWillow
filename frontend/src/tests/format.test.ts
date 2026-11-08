import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney } from '../lib/format';

describe('formatters', () => {
  it('formats CAD money with two decimals', () => {
    expect(formatMoney(1234.5)).toMatch(/1,234\.50/);
    expect(formatMoney(null)).toBe('—');
  });

  it('formats calendar dates without shifting the day', () => {
    expect(formatDate('2026-03-01')).toMatch(/Mar/);
    expect(formatDate('2026-03-01')).toMatch(/\b1\b/);
    expect(formatDate(undefined)).toBe('—');
  });
});
