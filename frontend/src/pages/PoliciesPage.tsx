import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Policy } from '../api/types';
import { Alert, Card, EmptyState, PageHeader, SkeletonRows } from '../components/ui';
import { PolicyStatusPill } from '../components/StatusPill';
import { formatDate, formatMoney } from '../lib/format';

export function usePolicies() {
  return useQuery({
    queryKey: ['policies', 'mine'],
    queryFn: ({ signal }) => api.get<Policy[]>('/policies/mine', signal),
  });
}

export function PoliciesPage() {
  const policies = usePolicies();
  return (
    <>
      <PageHeader title="My policies" subtitle="Home contents policies on your account." />
      <Card>
        {policies.isPending && <SkeletonRows />}
        {policies.isError && <div className="p-5"><Alert tone="error">Policies could not be loaded.</Alert></div>}
        {policies.data && policies.data.length === 0 && (
          <EmptyState title="No policies on your account">Contact your insurer if this looks wrong.</EmptyState>
        )}
        {policies.data && policies.data.length > 0 && (
          <ul className="divide-y divide-line" data-testid="policy-list">
            {policies.data.map((p) => (
              <li key={p.id} className="grid gap-3 px-5 py-4 sm:grid-cols-[1.4fr_1fr_1fr_1fr] sm:items-center"
                data-testid="policy-row">
                <div>
                  <p className="font-medium text-ink">{p.policyNumber}</p>
                  <p className="text-sm text-ink-muted">{p.productName} · {p.insuredAddress}</p>
                </div>
                <div className="text-sm">
                  <p className="text-ink-faint">Policy period</p>
                  <p className="text-ink">{formatDate(p.effectiveDate)} – {formatDate(p.expirationDate)}</p>
                </div>
                <div className="text-sm tabular">
                  <p className="text-ink-faint">Coverage limit / deductible</p>
                  <p className="text-ink">{formatMoney(p.coverageLimit)} / {formatMoney(p.deductible)}</p>
                </div>
                <div className="sm:text-right"><PolicyStatusPill status={p.status} /></div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
