import { Link, Navigate, useParams } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { useClaim } from '../api/claims';
import { isStaff } from '../api/types';
import { ClaimStatusPill } from '../components/ClaimStatusPill';
import { Alert, Card, CardHeader, EmptyState } from '../components/ui';
import { Spinner } from '../components/Spinner';
import { useCurrentUser } from '../features/auth/useAuth';
import { FactsCard, FindingsCard, SnapshotCard, TimelineCard } from '../features/claims/ClaimSections';
import { NotesCard } from '../features/claims/NotesCard';
import { OwnerResponseCard } from '../features/claims/OwnerResponseCard';
import { StaffReviewPanel } from '../features/claims/StaffReviewPanel';
import { EvidenceCard, EvidenceList } from '../features/attachments/EvidenceSection';

export function ClaimDetailPage() {
  const { id } = useParams();
  const me = useCurrentUser();
  const claim = useClaim(id);
  const staff = isStaff(me.data?.role);

  if (claim.isPending) return <Spinner label="Loading claim" />;
  if (claim.isError) {
    const notFound = claim.error instanceof ApiRequestError && claim.error.status === 404;
    return notFound
      ? <div data-testid="claim-not-found"><EmptyState title="Claim not found"
          action={<Link className="text-accent underline" to="/">Back to your home page</Link>}>
          This claim does not exist or you do not have access to it.</EmptyState></div>
      : <Alert tone="error">The claim could not be loaded. Refresh to try again.</Alert>;
  }
  const c = claim.data;
  if (c.status === 'DRAFT' && c.availableActions.includes('EDIT_DRAFT')) {
    return <Navigate to={`/claims/${c.id}/edit`} replace />;
  }
  const refresh = () => { void claim.refetch(); };

  return (
    <div data-testid="claim-detail" data-claim-id={c.id} data-version={c.version}>
      <div className="mb-6">
        <Link to={staff ? '/staff/queue' : '/claims'} className="text-sm text-accent hover:underline">
          ← {staff ? 'Work queue' : 'My claims'}
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight" data-testid="claim-reference">{c.reference}</h1>
          <ClaimStatusPill status={c.status} />
        </div>
        <p className="mt-1 text-ink-muted" data-testid="claim-title">{c.title}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="flex min-w-0 flex-col gap-6">
          {!staff && <OwnerResponseCard claim={c} onRefresh={refresh}
            evidence={<EvidenceList claimId={c.id} canUpload />} />}
          <Card>
            <CardHeader title="What happened" />
            <p className="whitespace-pre-wrap px-5 py-4 text-sm text-ink" data-testid="claim-description">
              {c.description}
            </p>
          </Card>
          <FindingsCard claim={c} />
          {(staff || c.status !== 'NEEDS_INFO') && (
            <EvidenceCard claimId={c.id} canUpload={c.availableActions.includes('UPLOAD_ATTACHMENT')} />
          )}
          <NotesCard claim={c} staff={staff} />
          <TimelineCard claimId={c.id} />
        </div>
        <aside className="flex flex-col gap-6 lg:sticky lg:top-4 lg:self-start" aria-label="Claim facts and actions">
          {staff && <StaffReviewPanel claim={c} onRefresh={refresh} />}
          <FactsCard claim={c} showOwner={staff} />
          <SnapshotCard claim={c} />
        </aside>
      </div>
    </div>
  );
}
