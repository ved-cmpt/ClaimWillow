import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, toQuery } from '../api/client';
import type { ClaimStatus } from '../api/claims';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { FilterDate, FilterSelect, rowClass, TableFrame, theadClass } from '../components/Filters';
import { Icon } from '../components/Icon';
import { Alert, buttonClass, Callout, PageHeader, SkeletonRows, Th } from '../components/ui';
import { formatMoney } from '../lib/format';

interface ReportSummary {
  rows: { status: ClaimStatus; count: number; totalEstimated: number }[];
  totalClaims: number;
  totalEstimated: number;
}

export function ReportsPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState('');
  const summary = useQuery({
    queryKey: ['claims', 'report', from, to],
    queryFn: ({ signal }) => api.get<ReportSummary>(
      `/staff/reports/summary${toQuery({ submittedFrom: from, submittedTo: to })}`, signal),
  });
  const csvUrl = `/api/v1/staff/reports/claims.csv${toQuery({ status, submittedFrom: from, submittedTo: to })}`;

  return (
    <>
      <PageHeader title="Reports" icon="chart" subtitle="Submitted claims by status, straight from the live database." />
      <TableFrame
        toolbar={
          <div className="flex flex-wrap items-center gap-2">
            <FilterDate label="From" accessibleLabel="Submitted from" id="report-from" value={from} onChange={(e) => setFrom(e.target.value)} />
            <FilterDate label="To" accessibleLabel="Submitted to" id="report-to" value={to} onChange={(e) => setTo(e.target.value)} />
            <FilterSelect label="Export status" icon="tag" id="report-status" value={status}
              onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {summary.data?.rows.map((r) => <option key={r.status} value={r.status}>{claimStatusLabel[r.status]}</option>)}
            </FilterSelect>
            <a href={csvUrl} download data-testid="export-csv" className={buttonClass('primary', 'sm:ml-auto')}>
              <Icon name="download" size={15} />Export CSV
            </a>
          </div>
        }>
        {summary.isPending && <SkeletonRows rows={6} />}
        {summary.isError && <div className="p-4"><Alert tone="error">The report could not be loaded.</Alert></div>}
        {summary.data && (
          <table className="w-full text-sm" data-testid="report-table">
            <caption className="sr-only">Claims by status</caption>
            <thead className={theadClass}>
              <tr>
                <Th icon="tag" className="pl-4 sm:pl-5">Status</Th>
                <Th icon="list" className="text-right">Claims</Th>
                <Th icon="money" className="pr-4 text-right sm:pr-5">Total estimated</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {summary.data.rows.map((r) => (
                <tr key={r.status} data-testid={`report-row-${r.status}`} className={rowClass}>
                  <td className="py-2.5 pl-4 pr-3 sm:pl-5"><ClaimStatusPill status={r.status} /></td>
                  <td className="px-3 py-2.5 text-right tabular">{r.count}</td>
                  <td className="py-2.5 pl-3 pr-4 text-right tabular sm:pr-5">{formatMoney(r.totalEstimated)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line-strong bg-canvas/60 font-semibold">
              <tr>
                <th scope="row" className="py-2.5 pl-4 pr-3 text-left sm:pl-5">Total</th>
                <td className="px-3 py-2.5 text-right tabular" data-testid="report-total">{summary.data.totalClaims}</td>
                <td className="py-2.5 pl-3 pr-4 text-right tabular sm:pr-5">{formatMoney(summary.data.totalEstimated)}</td>
              </tr>
            </tfoot>
          </table>
        )}
      </TableFrame>
      <Callout tone="neutral" className="mt-4">
        Estimates are policyholder estimates, not payable amounts. Drafts are excluded. Export is limited to 10,000 rows.
      </Callout>
    </>
  );
}
