import { Link, Navigate, useParams } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { useClaim } from '../api/claims';
import { isStaff } from '../api/types';
import { ClaimStatusPill } from '../components/ClaimStatusPill';
import { Alert, BlockHeading, Breadcrumbs, EmptyState, PageIcon } from '../components/ui';
import { Spinner } from '../components/Spinner';
import { homePathFor, useCurrentUser } from '../features/auth/useAuth';
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
  const home = me.data ? homePathFor(me.data) : '/';

  if (claim.isPending) return <Spinner label="Loading claim" />;
  if (claim.isError) {
    const notFound = claim.error instanceof ApiRequestError && claim.error.status === 404;
    return notFound
      ? <div data-testid="claim-not-found"><EmptyState title="Claim not found" icon="search"
          action={<Link className="font-medium text-accent underline underline-offset-2" to={home}>
            Back to your home page</Link>}>
          This claim does not exist or you do not have access to it.</EmptyState></div>
      : <Alert tone="error">The claim could not be loaded. Refresh to try again.</Alert>;
  }
  const c = claim.data;
  if (c.status === 'DRAFT' && c.availableActions.includes('EDIT_DRAFT')) {
    return <Navigate to={`/claims/${c.id}/edit`} replace />;
  }
  const refresh = () => { void claim.refetch(); };

  return (
    <article data-testid="claim-detail" data-claim-id={c.id} data-version={c.version} aria-labelledby="claim-heading">
      <Breadcrumbs items={[
        staff ? { label: 'Work queue', to: '/staff/queue' } : { label: 'My claims', to: '/claims' },
        { label: c.reference ?? 'Claim' },
      ]} />
      <header className="mb-6">
        <PageIcon name="file" />
        <div className="flex flex-wrap items-center gap-3">
          <h1 id="claim-heading" className="text-[1.75rem] font-bold leading-tight tracking-tight sm:text-[2rem]"
            data-testid="claim-reference">{c.reference}</h1>
          <ClaimStatusPill status={c.status} />
        </div>
        <p className="mt-1.5 text-[17px] text-ink-muted" data-testid="claim-title">{c.title}</p>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_20rem] xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex min-w-0 flex-col gap-8">
          <FactsCard claim={c} showOwner={staff} />
          {!staff && <OwnerResponseCard claim={c} onRefresh={refresh}
            evidence={<EvidenceList claimId={c.id} canUpload />} />}
          <section aria-labelledby="description-heading">
            <BlockHeading id="description-heading" title="What happened" icon="list" />
            <p className="whitespace-pre-wrap text-[15px] leading-relaxed text-ink" data-testid="claim-description">
              {c.description}
            </p>
          </section>
          <FindingsCard claim={c} />
          {(staff || c.status !== 'NEEDS_INFO') && (
            <EvidenceCard claimId={c.id} canUpload={c.availableActions.includes('UPLOAD_ATTACHMENT')} />
          )}
          <NotesCard claim={c} staff={staff} />
          <TimelineCard claimId={c.id} />
        </div>
        <aside className={`${staff ? 'order-first' : ''} flex flex-col gap-4 lg:order-none lg:sticky lg:top-8 lg:self-start`}
          aria-label="Claim actions and policy terms">
          {staff && <StaffReviewPanel claim={c} onRefresh={refresh} />}
          <SnapshotCard claim={c} />
        </aside>
      </div>
    </article>
  );
}
