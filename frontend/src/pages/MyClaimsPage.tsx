import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { CLAIM_STATUSES, useMyClaimSummary, useMyClaims } from '../api/claims';
import type { ClaimStatus, ClaimSummary, StatusCounts } from '../api/claims';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { Alert, Button, EmptyState, PageHeader, SkeletonRows, Th } from '../components/ui';
import { FilterSelect, rowClass, SearchInput, TableFrame, theadClass } from '../components/Filters';
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
      <PageHeader title="My claims" icon="inbox" subtitle="Track the claims you have started or submitted."
        actions={<Button icon="plus" onClick={() => navigate('/claims/new')} data-testid="new-claim">New claim</Button>} />

      <dl className="mb-8 grid grid-cols-2 gap-px overflow-hidden rounded-card border border-line bg-line md:grid-cols-4">
        {stats.map((s) => (
          <div key={s.label} className="bg-surface px-4 py-3">
            <dt className="text-xs font-medium text-ink-faint">{s.label}</dt>
            <dd className="mt-0.5 text-2xl font-semibold tabular text-ink" data-testid={s.testId}>
              {summary.isPending ? '–' : s.value}
            </dd>
          </div>
        ))}
      </dl>

      <TableFrame
        toolbar={
          <form role="search" className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center"
            onSubmit={(e) => { e.preventDefault(); update({ query: search.trim() }); }}>
            <FilterSelect label="Status" icon="tag" value={status} data-testid="status-filter"
              onChange={(e) => update({ status: e.target.value })}>
              <option value="">All</option>
              {CLAIM_STATUSES.map((s) => <option key={s} value={s}>{claimStatusLabel[s]}</option>)}
            </FilterSelect>
            <SearchInput id="claim-search" label="Search by reference or title" value={search}
              onChange={(e) => setSearch(e.target.value)} placeholder="Search by reference or title"
              data-testid="claim-search" className="sm:w-72" />
            <Button type="submit" variant="secondary">Search</Button>
          </form>
        }
        footer={claims.data && claims.data.items.length > 0 && (
          <Pager page={claims.data.page} totalPages={claims.data.totalPages}
            onPage={(p) => update({ page: String(p) })} />
        )}>
        {claims.isPending && <SkeletonRows />}
        {claims.isError && <div className="p-4"><Alert tone="error">Your claims could not be loaded.</Alert></div>}
        {claims.data && claims.data.items.length === 0 && (
          status || query
            ? <EmptyState title="No claims match these filters" icon="search">Try a different status or search term.</EmptyState>
            : <EmptyState title="You have no claims yet" icon="inbox"
                action={<Button icon="plus" onClick={() => navigate('/claims/new')}>Start a claim</Button>}>
                Start a claim for belongings that were damaged or lost. You can save a draft and finish later.
              </EmptyState>
        )}
        {claims.data && claims.data.items.length > 0 && (
          <table className="w-full text-sm" data-testid="my-claims-table">
            <thead className={`hidden md:table-header-group ${theadClass}`}>
              <tr>
                <Th icon="file" className="pl-4 sm:pl-5">Claim</Th>
                <Th icon="tag">Status</Th>
                <Th icon="calendar">Incident</Th>
                <Th icon="money" className="text-right">Estimate</Th>
                <Th icon="clock" className="pr-4 text-right sm:pr-5">Last updated</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {claims.data.items.map((c) => (
                <tr key={c.id} data-testid="claim-row" data-claim-id={c.id}
                  className={`grid grid-cols-2 gap-x-3 gap-y-1 px-4 py-3 md:table-row md:px-0 ${rowClass}`}>
                  <td className="col-span-2 md:py-2.5 md:pl-5 md:pr-3">
                    <Link to={claimLink(c)} className="font-medium text-ink underline decoration-line-strong
                      underline-offset-4 hover:decoration-accent" data-testid="claim-link">
                      {c.reference ?? 'Unsubmitted draft'}
                    </Link>
                    <p className="text-ink-muted">{c.title ?? 'Untitled'}</p>
                  </td>
                  <td className="md:px-3 md:py-2.5"><ClaimStatusPill status={c.status} /></td>
                  <td className="text-right text-ink-muted md:px-3 md:py-2.5 md:text-left">
                    {formatDate(c.incidentDate)}
                  </td>
                  <td className="tabular text-ink md:px-3 md:py-2.5 md:text-right">{formatMoney(c.estimatedLoss)}</td>
                  <td className="text-right text-xs text-ink-faint md:py-2.5 md:pl-3 md:pr-5">{formatDateTime(c.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </TableFrame>
    </>
  );
}

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between border-t border-line px-4 py-2.5 text-sm sm:px-5">
      <Button variant="secondary" icon="chevronLeft" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
      <span className="text-ink-muted">Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</Button>
    </nav>
  );
}
