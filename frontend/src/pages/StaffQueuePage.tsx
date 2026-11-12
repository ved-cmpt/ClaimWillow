import { useState } from 'react';
import { isNarrowScreen } from '../lib/motion';
import { Link, useSearchParams } from 'react-router-dom';
import { CLAIM_STATUSES } from '../api/claims';
import { OPEN_STATUSES, useAdjusters, useDashboard, useQueue } from '../api/staff';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { Alert, Button, buttonClass, EmptyState, PageHeader, SkeletonRows, Th } from '../components/ui';
import { FilterDate, FilterSelect, rowClass, SearchInput, TableFrame, theadClass } from '../components/Filters';
import { Icon } from '../components/Icon';
import { formatDate, formatDateTime, formatMoney } from '../lib/format';
import { Pager } from './MyClaimsPage';

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
  const [filtersOpen, setFiltersOpen] = useState(() => !isNarrowScreen());

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
      <PageHeader title="Work queue" icon="inbox"
        subtitle={dashboard.data ? `${dashboard.data.assignedToMe} open claim(s) assigned to you · `
          + `${dashboard.data.unassigned} unassigned` : 'Submitted claims awaiting staff action.'} />

      <div className="mb-8 grid grid-cols-3 gap-2 sm:gap-3">
        {kpis.map((k) => (
          <button key={k.label} type="button" onClick={() => update({ status: k.status })}
            aria-pressed={status === k.status}
            className={`rounded-card border bg-surface px-3 py-2.5 text-left shadow-soft transition-colors
              hover:border-ink-faint sm:px-4 sm:py-3 ${status === k.status ? 'border-accent ring-1 ring-accent'
              : 'border-line'}`}>
            <span className="block text-xs font-medium text-ink-faint">{k.label}</span>
            <span className="mt-0.5 block text-2xl font-semibold tabular text-ink" data-testid={k.testId}>
              {k.value ?? '–'}
            </span>
            <span className="hidden text-xs text-ink-muted sm:block">{k.hint}</span>
          </button>
        ))}
      </div>

      <TableFrame
        toolbar={
          <form role="search" className="flex flex-col gap-2"
            onSubmit={(e) => { e.preventDefault(); update({ query: search.trim() }); }}>
            <div className="flex flex-col gap-2 sm:flex-row">
              <SearchInput id="queue-search" label="Reference, title or policy" value={search}
                onChange={(e) => setSearch(e.target.value)} data-testid="queue-search"
                placeholder="Search reference, title or policy (e.g. HC-100421)" className="sm:flex-1" />
              <div className="flex gap-2">
                <Button type="submit" variant="secondary">Search</Button>
                <Button variant="ghost" onClick={() => { setSearch(''); setParams(new URLSearchParams(), { replace: true }); }}>
                  Reset filters
                </Button>
                <Button variant="secondary" icon="list" className="sm:hidden" aria-expanded={filtersOpen}
                  aria-controls="queue-filters" onClick={() => setFiltersOpen((o) => !o)}>
                  Filters
                </Button>
              </div>
            </div>
            <div id="queue-filters" className={`${filtersOpen ? 'grid' : 'hidden'} grid-cols-1 gap-2 sm:flex sm:flex-wrap`}>
              <FilterSelect label="Status" icon="tag" value={status} data-testid="queue-status"
                onChange={(e) => update({ status: e.target.value })}>
                <option value={OPEN_STATUSES}>All open</option>
                <option value="ALL">All statuses</option>
                {CLAIM_STATUSES.filter((s) => s !== 'DRAFT').map((s) => (
                  <option key={s} value={s}>{claimStatusLabel[s]}</option>
                ))}
              </FilterSelect>
              <FilterSelect label="Assigned to" icon="user" value={assignedTo} data-testid="queue-assigned"
                onChange={(e) => update({ assignedTo: e.target.value })}>
                <option value="">Anyone</option>
                <option value="me">Me</option>
                <option value="unassigned">Unassigned</option>
                {adjusters.data?.map((a) => <option key={a.id} value={a.id}>{a.displayName}</option>)}
              </FilterSelect>
              <FilterDate label="From" accessibleLabel="Submitted from" value={submittedFrom}
                onChange={(e) => update({ submittedFrom: e.target.value })} />
              <FilterDate label="To" accessibleLabel="Submitted to" value={submittedTo}
                onChange={(e) => update({ submittedTo: e.target.value })} />
              <FilterSelect label="Sort" icon="list" value={sort} onChange={(e) => update({ sort: e.target.value })}>
                <option value="submittedAt,desc">Newest submitted</option>
                <option value="submittedAt,asc">Oldest submitted</option>
                <option value="updatedAt,desc">Recently updated</option>
                <option value="estimatedLoss,desc">Largest estimate</option>
              </FilterSelect>
            </div>
          </form>
        }
        footer={queue.data && queue.data.items.length > 0 && (
          <>
            <p className="border-t border-line px-4 py-2 text-xs text-ink-faint sm:px-5">
              {queue.data.totalElements} claim(s) match.
            </p>
            <Pager page={queue.data.page} totalPages={queue.data.totalPages} onPage={(p) => update({ page: String(p) })} />
          </>
        )}>
        {queue.isPending && <SkeletonRows rows={5} />}
        {queue.isError && <div className="p-4"><Alert tone="error">The queue could not be loaded.</Alert></div>}
        {queue.data && queue.data.items.length === 0 && (
          <EmptyState title="No claims match these filters" icon="search">
            Adjust the filters or reset them to see all open claims.</EmptyState>
        )}
        {queue.data && queue.data.items.length > 0 && (
          <table className="w-full text-sm" data-testid="queue-table">
            <thead className={`hidden lg:table-header-group ${theadClass}`}>
              <tr>
                <Th icon="file" className="pl-5">Claim</Th>
                <Th icon="tag">Status</Th>
                <Th icon="user">Adjuster</Th>
                <Th icon="money" className="text-right">Estimate</Th>
                <Th icon="calendar">Incident</Th>
                <Th icon="clock">Submitted</Th>
                <Th className="pr-5"><span className="sr-only">Action</span></Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {queue.data.items.map((c) => (
                <tr key={c.id} data-testid="queue-row" data-claim-id={c.id} data-reference={c.reference ?? ''}
                  className={`grid grid-cols-2 gap-x-3 gap-y-1 px-4 py-3 lg:table-row lg:px-0 ${rowClass}`}>
                  <td className="col-span-2 lg:py-2.5 lg:pl-5 lg:pr-3">
                    <p className="font-medium text-ink">{c.reference}</p>
                    <p className="text-ink-muted">{c.title}</p>
                    <p className="text-xs text-ink-faint">{c.owner?.displayName} · {c.policyNumber}</p>
                  </td>
                  <td className="lg:px-3 lg:py-2.5"><ClaimStatusPill status={c.status} /></td>
                  <td className="text-right lg:whitespace-nowrap lg:px-3 lg:py-2.5 lg:text-left" data-testid="queue-adjuster">
                    {c.assignedAdjuster?.displayName ?? <span className="text-ink-faint">Unassigned</span>}
                  </td>
                  <td className="tabular lg:px-3 lg:py-2.5 lg:text-right">{formatMoney(c.estimatedLoss)}</td>
                  <td className="text-right text-ink-muted lg:whitespace-nowrap lg:px-3 lg:py-2.5 lg:text-left">
                    <span className="lg:hidden">Incident </span>{formatDate(c.incidentDate)}
                  </td>
                  <td className="text-xs text-ink-faint lg:px-3 lg:py-2.5">{formatDateTime(c.submittedAt)}</td>
                  <td className="text-right lg:py-2.5 lg:pl-3 lg:pr-5">
                    <Link to={`/claims/${c.id}`} data-testid="queue-open" className={buttonClass('secondary', 'py-1')}>
                      Open<span className="sr-only"> claim {c.reference}</span>
                      <Icon name="arrowRight" size={14} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </TableFrame>
    </>
  );
}
