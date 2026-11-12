import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Policy } from '../api/types';
import { Icon } from '../components/Icon';
import { Alert, EmptyState, PageHeader, SkeletonRows } from '../components/ui';
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
      <PageHeader title="My policies" icon="shield" subtitle="Home contents policies on your account." />
      <section className="overflow-hidden rounded-card border border-line bg-surface shadow-soft">
        {policies.isPending && <SkeletonRows />}
        {policies.isError && <div className="p-4"><Alert tone="error">Policies could not be loaded.</Alert></div>}
        {policies.data && policies.data.length === 0 && (
          <EmptyState title="No policies on your account">Contact your insurer if this looks wrong.</EmptyState>
        )}
        {policies.data && policies.data.length > 0 && (
          <ul className="divide-y divide-line" data-testid="policy-list">
            {policies.data.map((p) => (
              <li key={p.id} className="grid gap-3 px-4 py-4 transition-colors hover:bg-subtle sm:grid-cols-[1.4fr_1fr_1fr_auto]
                sm:items-center sm:px-5" data-testid="policy-row">
                <div className="flex items-start gap-3">
                  <span aria-hidden="true" className="mt-0.5 inline-flex h-8 w-8 shrink-0 items-center justify-center
                    rounded-md bg-accent-soft text-accent"><Icon name="home" size={17} /></span>
                  <div>
                    <p className="font-medium text-ink">{p.policyNumber}</p>
                    <p className="text-sm text-ink-muted">{p.productName} · {p.insuredAddress}</p>
                  </div>
                </div>
                <div className="text-sm">
                  <p className="text-xs text-ink-faint">Policy period</p>
                  <p className="text-ink">{formatDate(p.effectiveDate)} – {formatDate(p.expirationDate)}</p>
                </div>
                <div className="text-sm tabular">
                  <p className="text-xs text-ink-faint">Coverage limit / deductible</p>
                  <p className="text-ink">{formatMoney(p.coverageLimit)} / {formatMoney(p.deductible)}</p>
                </div>
                <div className="sm:text-right"><PolicyStatusPill status={p.status} /></div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
