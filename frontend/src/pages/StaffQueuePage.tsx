import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CLAIM_STATUSES } from '../api/claims';
import { OPEN_STATUSES, useAdjusters, useDashboard, useQueue } from '../api/staff';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { Alert, Button, Card, EmptyState, PageHeader, SkeletonRows } from '../components/ui';
import { formatDate, formatDateTime, formatMoney } from '../lib/format';
import { Pager } from './MyClaimsPage';

const selectClass = 'rounded-md border border-line-strong bg-surface px-3 py-2 text-sm';

export function StaffQueuePage() {
  const [params, setParams] = useSearchParams();
  const get = (k: string, fallback = '') => params.get(k) ?? fallback;
  const status = get('status', OPEN_STATUSES);
  const assignedTo = get('assignedTo');
  const query = get('query');
  const submittedFrom = get('submittedFrom');
  const submittedTo = get('submittedTo');
  const sort = get('sort', 'submittedAt,desc');
  const page = Number(get('page', '0'));
  const [search, setSearch] = useState(query);

  const queue = useQueue({ status: status === 'ALL' ? undefined : status, assignedTo, query, submittedFrom,
    submittedTo, sort, page, size: 15 });
  const dashboard = useDashboard();
  const adjusters = useAdjusters();

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? merged.set(k, v) : merged.delete(k)));
    if (!('page' in next)) merged.delete('page');
    setParams(merged, { replace: true });
  };

  const kpis = [
    { label: 'New', hint: 'Submitted, not yet in review', value: dashboard.data?.newClaims, status: 'SUBMITTED',
      testId: 'kpi-new' },
    { label: 'In review', hint: 'Being assessed', value: dashboard.data?.inReview, status: 'IN_REVIEW',
      testId: 'kpi-in-review' },
    { label: 'Needs info', hint: 'Waiting on the policyholder', value: dashboard.data?.needsInfo,
      status: 'NEEDS_INFO', testId: 'kpi-needs-info' },
  ];

  return (
    <>
      <PageHeader title="Work queue"
        subtitle={dashboard.data ? `${dashboard.data.assignedToMe} open claim(s) assigned to you · `
          + `${dashboard.data.unassigned} unassigned` : 'Submitted claims awaiting staff action.'} />

      <div className="mb-6 grid grid-cols-3 gap-2 sm:gap-3">
        {kpis.map((k) => (
          <button key={k.label} type="button" onClick={() => update({ status: k.status })}
            aria-pressed={status === k.status}
            className={`rounded-card border bg-surface px-3 py-2.5 text-left hover:border-accent sm:px-4 sm:py-3 ${status === k.status
              ? 'border-accent ring-1 ring-accent' : 'border-line'}`}>
            <span className="block text-xs font-medium uppercase tracking-wide text-ink-faint">{k.label}</span>
            <span className="mt-1 block text-2xl font-semibold tabular text-ink" data-testid={k.testId}>
              {k.value ?? '–'}
            </span>
            <span className="hidden text-xs text-ink-muted sm:block">{k.hint}</span>
          </button>
        ))}
      </div>

      <Card>
        <form role="search" className="grid gap-3 border-b border-line px-5 py-4 md:grid-cols-6 md:items-end"
          onSubmit={(e) => { e.preventDefault(); update({ query: search.trim() }); }}>
          <div className="flex flex-col gap-1.5 md:col-span-2">
            <label htmlFor="queue-search" className="text-sm font-medium">Reference, title or policy</label>
            <input id="queue-search" type="search" value={search} onChange={(e) => setSearch(e.target.value)}
              className={selectClass} data-testid="queue-search" placeholder="e.g. CW-2026-… or HC-100421" />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="queue-status" className="text-sm font-medium">Status</label>
            <select id="queue-status" className={selectClass} value={status} data-testid="queue-status"
              onChange={(e) => update({ status: e.target.value })}>
              <option value={OPEN_STATUSES}>All open</option>
              <option value="ALL">All statuses</option>
              {CLAIM_STATUSES.filter((s) => s !== 'DRAFT').map((s) => (
                <option key={s} value={s}>{claimStatusLabel[s]}</option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="queue-assigned" className="text-sm font-medium">Assigned to</label>
            <select id="queue-assigned" className={selectClass} value={assignedTo} data-testid="queue-assigned"
              onChange={(e) => update({ assignedTo: e.target.value })}>
              <option value="">Anyone</option>
              <option value="me">Me</option>
              <option value="unassigned">Unassigned</option>
              {adjusters.data?.map((a) => <option key={a.id} value={a.id}>{a.displayName}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="queue-from" className="text-sm font-medium">Submitted from</label>
            <input id="queue-from" type="date" className={selectClass} value={submittedFrom}
              onChange={(e) => update({ submittedFrom: e.target.value })} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="queue-to" className="text-sm font-medium">Submitted to</label>
            <input id="queue-to" type="date" className={selectClass} value={submittedTo}
              onChange={(e) => update({ submittedTo: e.target.value })} />
          </div>
          <div className="flex flex-col gap-1.5 md:col-span-2">
            <label htmlFor="queue-sort" className="text-sm font-medium">Sort</label>
            <select id="queue-sort" className={selectClass} value={sort} onChange={(e) => update({ sort: e.target.value })}>
              <option value="submittedAt,desc">Newest submitted first</option>
              <option value="submittedAt,asc">Oldest submitted first</option>
              <option value="updatedAt,desc">Recently updated</option>
              <option value="estimatedLoss,desc">Largest estimate first</option>
            </select>
          </div>
          <div className="flex gap-2 md:col-span-4 md:justify-end">
            <Button type="submit" variant="secondary">Search</Button>
            <Button variant="ghost" onClick={() => { setSearch(''); setParams(new URLSearchParams(), { replace: true }); }}>
              Reset filters
            </Button>
          </div>
        </form>

        {queue.isPending && <SkeletonRows rows={5} />}
        {queue.isError && <div className="p-5"><Alert tone="error">The queue could not be loaded.</Alert></div>}
        {queue.data && queue.data.items.length === 0 && (
          <EmptyState title="No claims match these filters">Adjust the filters or reset them to see all open claims.</EmptyState>
        )}
        {queue.data && queue.data.items.length > 0 && (
          <>
            <table className="w-full text-sm" data-testid="queue-table">
              <thead className="hidden border-b border-line bg-subtle text-left text-xs uppercase tracking-wide
                text-ink-faint lg:table-header-group">
                <tr>
                  <th scope="col" className="px-5 py-2.5 font-medium">Claim</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Status</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Adjuster</th>
                  <th scope="col" className="px-3 py-2.5 text-right font-medium">Estimate</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Incident</th>
                  <th scope="col" className="px-3 py-2.5 font-medium">Submitted</th>
                  <th scope="col" className="px-5 py-2.5 font-medium"><span className="sr-only">Action</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {queue.data.items.map((c) => (
                  <tr key={c.id} data-testid="queue-row" data-claim-id={c.id} data-reference={c.reference ?? ''}
                    className="grid grid-cols-2 gap-x-3 gap-y-1 px-5 py-3 lg:table-row lg:px-0">
                    <td className="col-span-2 lg:px-5 lg:py-3">
                      <p className="font-medium text-ink">{c.reference}</p>
                      <p className="text-ink-muted">{c.title}</p>
                      <p className="text-xs text-ink-faint">{c.owner?.displayName} · {c.policyNumber}</p>
                    </td>
                    <td className="lg:px-3 lg:py-3"><ClaimStatusPill status={c.status} /></td>
                    <td className="text-right lg:px-3 lg:py-3 lg:text-left" data-testid="queue-adjuster">
                      {c.assignedAdjuster?.displayName ?? <span className="text-ink-faint">Unassigned</span>}
                    </td>
                    <td className="tabular lg:px-3 lg:py-3 lg:text-right">{formatMoney(c.estimatedLoss)}</td>
                    <td className="text-right text-ink-muted lg:px-3 lg:py-3 lg:text-left">
                      <span className="lg:hidden">Incident </span>{formatDate(c.incidentDate)}
                    </td>
                    <td className="text-xs text-ink-faint lg:px-3 lg:py-3 lg:text-sm">{formatDateTime(c.submittedAt)}</td>
                    <td className="text-right lg:px-5 lg:py-3">
                      <Link to={`/claims/${c.id}`} data-testid="queue-open"
                        className="inline-flex rounded-md border border-line-strong px-3 py-1.5 text-sm font-medium
                          text-accent hover:bg-accent-soft">
                        Open<span className="sr-only"> claim {c.reference}</span>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="border-t border-line px-5 py-2 text-xs text-ink-faint">
              {queue.data.totalElements} claim(s) match.
            </p>
            <Pager page={queue.data.page} totalPages={queue.data.totalPages} onPage={(p) => update({ page: String(p) })} />
          </>
        )}
      </Card>
    </>
  );
}
