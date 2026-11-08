import { describe, expect, it } from 'vitest';
import { toQuery, readCookie } from '../api/client';

describe('api client helpers', () => {
  it('builds query strings while skipping empty values', () => {
    expect(toQuery({ status: 'SUBMITTED', page: 0, query: '', assignedTo: undefined })).toBe(
      '?status=SUBMITTED&page=0',
    );
    expect(toQuery({})).toBe('');
  });

  it('reads cookies by exact name', () => {
    document.cookie = 'XSRF-TOKEN=abc123';
    document.cookie = 'OTHER=1';
    expect(readCookie('XSRF-TOKEN')).toBe('abc123');
    expect(readCookie('MISSING')).toBeUndefined();
  });
});
