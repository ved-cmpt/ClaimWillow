/**
 * Same-origin JSON API client. Sends the session cookie automatically and echoes the
 * XSRF-TOKEN cookie in the X-XSRF-TOKEN header on state-changing requests.
 */

export interface FieldViolation {
  field: string;
  message: string;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldViolation[];
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, fieldErrors: FieldViolation[] = [],
    details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.details = details;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }
}

export const API_BASE = '/api/v1';

export function readCookie(name: string): string | undefined {
  const match = document.cookie
    .split(';')
    .map((c) => c.trim())
    .find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : undefined;
}

let csrfPromise: Promise<void> | null = null;

/** Ensures the CSRF cookie exists (the backend sets it on any request, including this one). */
export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return;
  if (!csrfPromise) {
    csrfPromise = fetch(`${API_BASE}/auth/csrf`, { credentials: 'same-origin' })
      .then(() => undefined)
      .finally(() => {
        csrfPromise = null;
      });
  }
  await csrfPromise;
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

interface RequestOptions {
  body?: unknown;
  formData?: FormData;
  signal?: AbortSignal;
}

async function parseError(response: Response): Promise<ApiRequestError> {
  let code = `HTTP_${response.status}`;
  let message = 'Something went wrong. Please try again.';
  let fieldErrors: FieldViolation[] = [];
  let details: Record<string, unknown> = {};
  try {
    const body = await response.json();
    code = body.code ?? code;
    message = body.message ?? message;
    fieldErrors = body.fieldErrors ?? [];
    details = body.details ?? {};
  } catch {
    if (response.status === 403) {
      message = 'Your session security token expired. Refresh the page and try again.';
    }
  }
  return new ApiRequestError(response.status, code, message, fieldErrors, details);
}

export async function request<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    await ensureCsrfCookie();
    const token = readCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body,
    credentials: 'same-origin',
    signal: options.signal,
  });
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/json')) {
    return (await response.json()) as T;
  }
  return undefined as T;
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>('GET', path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
  upload: <T>(path: string, formData: FormData) => request<T>('POST', path, { formData }),
};

export function toQuery(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}
