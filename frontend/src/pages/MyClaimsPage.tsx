import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { CLAIM_STATUSES, useMyClaimSummary, useMyClaims } from '../api/claims';
import type { ClaimStatus, ClaimSummary, StatusCounts } from '../api/claims';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { Alert, Button, Card, EmptyState, PageHeader, SkeletonRows } from '../components/ui';
import { formatDate, formatDateTime, formatMoney } from '../lib/format';

function count(summary: StatusCounts | undefined, ...statuses: ClaimStatus[]): number {
  return summary?.counts.filter((c) => statuses.includes(c.status)).reduce((n, c) => n + c.count, 0) ?? 0;
}

export function claimLink(c: Pick<ClaimSummary, 'id' | 'status'>): string {
  return c.status === 'DRAFT' ? `/claims/${c.id}/edit` : `/claims/${c.id}`;
}

export function MyClaimsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const query = params.get('query') ?? '';
  const page = Number(params.get('page') ?? '0');
  const [search, setSearch] = useState(query);
  const claims = useMyClaims({ status, query, page, size: 10 });
  const summary = useMyClaimSummary();

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? merged.set(k, v) : merged.delete(k)));
    if (!('page' in next)) merged.delete('page');
    setParams(merged, { replace: true });
  };

  const stats = [
    { label: 'Drafts', value: count(summary.data, 'DRAFT'), testId: 'summary-drafts' },
    { label: 'With the adjuster', value: count(summary.data, 'SUBMITTED', 'IN_REVIEW'), testId: 'summary-open' },
    { label: 'Needs your input', value: count(summary.data, 'NEEDS_INFO'), testId: 'summary-needs-info' },
    { label: 'Decided or closed', value: count(summary.data, 'APPROVED', 'REJECTED', 'CLOSED'),
      testId: 'summary-done' },
  ];

  return (
    <>
      <PageHeader title="My claims" subtitle="Track the claims you have started or submitted."
        actions={<Button onClick={() => navigate('/claims/new')} data-testid="new-claim">New claim</Button>} />

      <dl className="mb-6 grid grid-cols-2 gap-3 md:grid-cols-4">
        {stats.map((s) => (
          <div key={s.label} className="rounded-card border border-line bg-surface px-4 py-3">
            <dt className="text-xs font-medium uppercase tracking-wide text-ink-faint">{s.label}</dt>
            <dd className="mt-1 text-2xl font-semibold tabular text-ink" data-testid={s.testId}>
              {summary.isPending ? '–' : s.value}
            </dd>
          </div>
        ))}
      </dl>

      <Card>
        <form role="search" className="flex flex-col gap-3 border-b border-line px-5 py-4 sm:flex-row sm:items-end"
          onSubmit={(e) => { e.preventDefault(); update({ query: search.trim() }); }}>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="status-filter" className="text-sm font-medium">Status</label>
            <select id="status-filter" value={status} data-testid="status-filter"
              onChange={(e) => update({ status: e.target.value })}
              className="rounded-md border border-line-strong bg-surface px-3 py-2 text-sm">
              <option value="">All statuses</option>
              {CLAIM_STATUSES.map((s) => <option key={s} value={s}>{claimStatusLabel[s]}</option>)}
            </select>
          </div>
          <div className="flex flex-1 flex-col gap-1.5">
            <label htmlFor="claim-search" className="text-sm font-medium">Search by reference or title</label>
            <input id="claim-search" type="search" value={search} onChange={(e) => setSearch(e.target.value)}
              placeholder="e.g. CW-2026-…" data-testid="claim-search"
              className="rounded-md border border-line-strong bg-surface px-3 py-2 text-sm" />
          </div>
          <Button type="submit" variant="secondary">Search</Button>
        </form>

        {claims.isPending && <SkeletonRows />}
        {claims.isError && <div className="p-5"><Alert tone="error">Your claims could not be loaded.</Alert></div>}
        {claims.data && claims.data.items.length === 0 && (
          status || query
            ? <EmptyState title="No claims match these filters">Try a different status or search term.</EmptyState>
            : <EmptyState title="You have no claims yet"
                action={<Button onClick={() => navigate('/claims/new')}>Start a claim</Button>}>
                Start a claim for belongings that were damaged or lost. You can save a draft and finish later.
              </EmptyState>
        )}
        {claims.data && claims.data.items.length > 0 && (
          <>
            <table className="w-full text-sm" data-testid="my-claims-table">
              <thead className="hidden border-b border-line bg-subtle text-left text-xs uppercase tracking-wide
                text-ink-faint md:table-header-group">
                <tr>
                  <th scope="col" className="px-5 py-2.5 font-medium">Claim</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Status</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Incident</th>
                  <th scope="col" className="px-3 py-2.5 text-right font-medium">Estimate</th>
                  <th scope="col" className="px-5 py-2.5 text-right font-medium">Last updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {claims.data.items.map((c) => (
                  <tr key={c.id} data-testid="claim-row" data-claim-id={c.id}
                    className="grid grid-cols-2 gap-x-3 gap-y-1 px-5 py-3 md:table-row md:px-0">
                    <td className="col-span-2 md:px-5 md:py-3">
                      <Link to={claimLink(c)} className="font-medium text-accent hover:underline"
                        data-testid="claim-link">
                        {c.reference ?? 'Unsubmitted draft'}
                      </Link>
                      <p className="text-ink-muted">{c.title ?? 'Untitled'}</p>
                    </td>
                    <td className="md:px-3 md:py-3"><ClaimStatusPill status={c.status} /></td>
                    <td className="text-right text-ink-muted md:px-3 md:py-3 md:text-left">
                      {formatDate(c.incidentDate)}
                    </td>
                    <td className="tabular text-ink md:px-3 md:py-3 md:text-right">{formatMoney(c.estimatedLoss)}</td>
                    <td className="text-right text-xs text-ink-faint md:px-5 md:py-3">{formatDateTime(c.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pager page={claims.data.page} totalPages={claims.data.totalPages}
              onPage={(p) => update({ page: String(p) })} />
          </>
        )}
      </Card>
    </>
  );
}

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between border-t border-line px-5 py-3 text-sm">
      <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
      <span className="text-ink-muted">Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</Button>
    </nav>
  );
}
