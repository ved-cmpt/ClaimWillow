import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, toQuery } from '../api/client';
import type { ClaimStatus } from '../api/claims';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { Alert, Card, CardHeader, PageHeader, SkeletonRows } from '../components/ui';
import { formatMoney } from '../lib/format';

interface ReportSummary {
  rows: { status: ClaimStatus; count: number; totalEstimated: number }[];
  totalClaims: number;
  totalEstimated: number;
}

const inputClass = 'rounded-md border border-line-strong bg-surface px-3 py-2 text-sm';

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
      <PageHeader title="Reports" subtitle="Submitted claims by status, straight from the live database." />
      <Card className="mb-6">
        <div className="grid gap-3 px-5 py-4 sm:grid-cols-4 sm:items-end">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="report-from" className="text-sm font-medium">Submitted from</label>
            <input id="report-from" type="date" className={inputClass} value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="report-to" className="text-sm font-medium">Submitted to</label>
            <input id="report-to" type="date" className={inputClass} value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="report-status" className="text-sm font-medium">Export status</label>
            <select id="report-status" className={inputClass} value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {summary.data?.rows.map((r) => <option key={r.status} value={r.status}>{claimStatusLabel[r.status]}</option>)}
            </select>
          </div>
          <a href={csvUrl} download data-testid="export-csv"
            className="inline-flex items-center justify-center rounded-md border border-accent bg-accent px-3.5 py-2
              text-sm font-medium text-white hover:bg-accent-strong">Export CSV</a>
        </div>
      </Card>
      <Card>
        <CardHeader title="Claims by status" />
        {summary.isPending && <SkeletonRows rows={6} />}
        {summary.isError && <div className="p-5"><Alert tone="error">The report could not be loaded.</Alert></div>}
        {summary.data && (
          <table className="w-full text-sm" data-testid="report-table">
            <thead className="border-b border-line bg-subtle text-left text-xs uppercase tracking-wide text-ink-faint">
              <tr>
                <th scope="col" className="px-5 py-2.5 font-medium">Status</th>
                <th scope="col" className="px-5 py-2.5 text-right font-medium">Claims</th>
                <th scope="col" className="px-5 py-2.5 text-right font-medium">Total estimated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {summary.data.rows.map((r) => (
                <tr key={r.status} data-testid={`report-row-${r.status}`}>
                  <td className="px-5 py-2.5"><ClaimStatusPill status={r.status} /></td>
                  <td className="px-5 py-2.5 text-right tabular">{r.count}</td>
                  <td className="px-5 py-2.5 text-right tabular">{formatMoney(r.totalEstimated)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-line-strong font-semibold">
              <tr>
                <th scope="row" className="px-5 py-2.5 text-left">Total</th>
                <td className="px-5 py-2.5 text-right tabular" data-testid="report-total">{summary.data.totalClaims}</td>
                <td className="px-5 py-2.5 text-right tabular">{formatMoney(summary.data.totalEstimated)}</td>
              </tr>
            </tfoot>
          </table>
        )}
      </Card>
      <p className="mt-4 text-xs text-ink-faint">
        Estimates are policyholder estimates, not payable amounts. Drafts are excluded. Export is limited to 10,000 rows.
      </p>
    </>
  );
}
