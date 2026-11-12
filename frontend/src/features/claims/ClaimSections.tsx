import { Link } from 'react-router-dom';
import { useFindings, useRules, useTimeline } from '../../api/claims';
import type { ClaimDetail, Finding, TimelineEvent } from '../../api/claims';
import { Avatar } from '../../components/AppShell';
import { ClaimStatusPill, claimStatusLabel } from '../../components/ClaimStatusPill';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/StatusPill';
import { Alert, BlockHeading, Callout, Card, CardHeader, EmptyState, PropertyList, SkeletonRows, Toggle }
  from '../../components/ui';
import type { Property } from '../../components/ui';
import { formatDate, formatDateTime, formatMoney } from '../../lib/format';

/** Page properties for a claim: status, policy, incident, estimate, people and dates. */
export function FactsCard({ claim, showOwner }: { claim: ClaimDetail; showOwner: boolean }) {
  const items: Property[] = [
    { label: 'Status', icon: 'tag', value: <ClaimStatusPill status={claim.status} /> },
    { label: 'Policy', icon: 'shield',
      value: claim.policySnapshot?.policyNumber ?? claim.policy?.policyNumber ?? '—' },
    { label: 'Incident date', icon: 'calendar', value: formatDate(claim.incidentDate), testId: 'fact-incident-date' },
    { label: 'Type', icon: 'layers',
      value: claim.incidentType === 'LOSS' ? 'Lost or stolen' : claim.incidentType === 'DAMAGE' ? 'Damaged' : '—' },
    { label: 'Estimated loss', icon: 'money', value: formatMoney(claim.estimatedLoss), testId: 'fact-estimate' },
  ];
  if (showOwner) items.push({ label: 'Policyholder', icon: 'user', value: claim.owner?.displayName ?? '—' });
  items.push({
    label: 'Adjuster', icon: 'user', testId: 'fact-adjuster',
    value: claim.assignedAdjuster?.displayName ?? <span className="text-ink-faint">Not yet assigned</span>,
  });
  items.push({ label: 'Submitted', icon: 'clock', value: formatDateTime(claim.submittedAt) });
  if (claim.closedAt) items.push({ label: 'Closed', icon: 'clock', value: formatDateTime(claim.closedAt) });
  return (
    <section aria-label="Claim properties" data-testid="claim-properties" className="border-b border-line pb-4">
      <PropertyList items={items} />
    </section>
  );
}

export function SnapshotCard({ claim }: { claim: ClaimDetail }) {
  const s = claim.policySnapshot;
  if (!s) return null;
  return (
    <Card data-testid="policy-snapshot">
      <CardHeader title="Policy snapshot" icon="copy" />
      <div className="px-4 py-3 text-sm sm:px-5">
        <p className="text-ink-muted">Terms copied from policy {s.policyNumber} when the claim was submitted.</p>
        <dl className="mt-3 grid grid-cols-2 gap-3">
          <div><dt className="text-xs text-ink-faint">Coverage limit</dt><dd className="tabular">{formatMoney(s.coverageLimit)}</dd></div>
          <div><dt className="text-xs text-ink-faint">Deductible</dt><dd className="tabular">{formatMoney(s.deductible)}</dd></div>
          <div className="col-span-2"><dt className="text-xs text-ink-faint">Policy period</dt>
            <dd>{formatDate(s.effectiveDate)} – {formatDate(s.expirationDate)}</dd></div>
        </dl>
      </div>
    </Card>
  );
}

function FindingItem({ finding }: { finding: Finding }) {
  const warning = finding.severity === 'WARNING';
  return (
    <li data-testid="finding" data-rule={finding.ruleCode}>
      <Callout tone={warning ? 'warning' : 'info'}
        title={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span>{finding.shortCode} · {finding.ruleTitle}</span>
            <Pill tone={warning ? 'warning' : 'info'} label={warning ? 'Needs review' : 'For information'} />
            <span className="text-xs font-normal text-ink-faint">v{finding.ruleVersion}</span>
          </span>
        }>
        <p>{finding.explanation}</p>
        {finding.relatedClaim && (
          <p className="mt-1">
            Possible match: <Link className="font-medium text-accent underline underline-offset-2"
              to={`/claims/${finding.relatedClaim.id}`}>{finding.relatedClaim.reference}</Link>
          </p>
        )}
      </Callout>
    </li>
  );
}

export function FindingsCard({ claim }: { claim: ClaimDetail }) {
  const findings = useFindings(claim.id, claim.status !== 'DRAFT');
  const rules = useRules();
  if (claim.status === 'DRAFT') return null;
  return (
    <section data-testid="findings-card" aria-labelledby="findings-heading">
      <BlockHeading id="findings-heading" title="Automated checks" icon="checkCircle" />
      {findings.isPending && <SkeletonRows rows={2} />}
      {findings.isError && <Alert tone="error">Checks could not be loaded.</Alert>}
      {findings.data && findings.data.length === 0 && (
        <Callout tone="success" testId="no-findings">
          No findings. All automated checks passed for this claim.
        </Callout>
      )}
      {findings.data && findings.data.length > 0 && (
        <ul className="flex flex-col gap-2">{findings.data.map((f) => <FindingItem key={f.id} finding={f} />)}</ul>
      )}
      <Toggle summary="How these checks work" className="mt-2" testId="checks-toggle">
        <p className="text-sm text-ink-muted">
          Checks are deterministic rules applied on submission and after each policyholder response. Findings never
          reject a claim on their own; an adjuster reviews them and records a decision.
          {claim.lastValidatedAt && <> Last run {formatDateTime(claim.lastValidatedAt)}.</>}
        </p>
        <ul className="mt-2 flex flex-col gap-1.5 text-sm">
          {rules.data?.map((r) => (
            <li key={r.code}><span className="font-medium">{r.shortCode} {r.title}</span>
              <span className="text-ink-muted"> — {r.description}</span></li>
          ))}
        </ul>
      </Toggle>
    </section>
  );
}

function statusChange(e: TimelineEvent): string | null {
  if (!e.toStatus) return null;
  return e.fromStatus
    ? `${claimStatusLabel[e.fromStatus]} → ${claimStatusLabel[e.toStatus]}`
    : claimStatusLabel[e.toStatus];
}

/** Quiet activity feed of the append-only audit events, oldest first. */
export function TimelineCard({ claimId }: { claimId: string }) {
  const timeline = useTimeline(claimId);
  return (
    <section data-testid="timeline-card" aria-labelledby="history-heading">
      <BlockHeading id="history-heading" title="History" icon="history" />
      {timeline.isPending && <SkeletonRows rows={3} />}
      {timeline.isError && <Alert tone="error">History could not be loaded.</Alert>}
      {timeline.data && timeline.data.length === 0 && <EmptyState title="No activity yet" icon="history" />}
      {timeline.data && timeline.data.length > 0 && (
        <ol className="flex flex-col">
          {timeline.data.map((e, i) => {
            const actor = e.actor?.displayName ?? 'System';
            const change = statusChange(e);
            return (
              <li key={e.id} className="relative flex gap-3 pb-4 last:pb-0" data-testid="timeline-event"
                data-event-type={e.type}>
                {i < timeline.data.length - 1 && (
                  <span aria-hidden="true" className="absolute bottom-0 left-3 top-7 w-px bg-line" />
                )}
                {e.actor ? <Avatar name={actor} size="sm" /> : (
                  <span aria-hidden="true" className="inline-flex h-6 w-6 shrink-0 items-center justify-center
                    rounded-full bg-subtle text-ink-faint"><Icon name="sparkle" size={13} /></span>
                )}
                <div className="min-w-0 pt-0.5 text-sm">
                  <p className="text-ink">
                    <span className="font-medium">{actor}</span>
                    <span className="text-ink-muted"> · </span>
                    {e.summary}
                  </p>
                  <p className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-ink-faint">
                    <span>{formatDateTime(e.occurredAt)}</span>
                    {change && <span className="font-medium text-ink-muted">{change}</span>}
                    {e.staffOnly && <span className="rounded bg-subtle px-1 font-medium">Staff only</span>}
                  </p>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
