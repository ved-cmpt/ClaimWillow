import { Link } from 'react-router-dom';
import { useFindings, useRules, useTimeline } from '../../api/claims';
import type { ClaimDetail, Finding, TimelineEvent } from '../../api/claims';
import { claimStatusLabel } from '../../components/ClaimStatusPill';
import { Pill } from '../../components/StatusPill';
import { Alert, Card, CardHeader, EmptyState, SkeletonRows } from '../../components/ui';
import { formatDate, formatDateTime, formatMoney } from '../../lib/format';

export function FactsCard({ claim, showOwner }: { claim: ClaimDetail; showOwner: boolean }) {
  const rows: [string, string, string?][] = [
    ['Incident date', formatDate(claim.incidentDate), 'fact-incident-date'],
    ['Type', claim.incidentType === 'LOSS' ? 'Lost or stolen' : claim.incidentType === 'DAMAGE' ? 'Damaged' : '—'],
    ['Estimated loss', formatMoney(claim.estimatedLoss), 'fact-estimate'],
    ['Policy', claim.policySnapshot?.policyNumber ?? claim.policy?.policyNumber ?? '—'],
    ['Submitted', formatDateTime(claim.submittedAt)],
  ];
  if (showOwner) rows.push(['Policyholder', claim.owner?.displayName ?? '—']);
  rows.push(['Adjuster', claim.assignedAdjuster?.displayName ?? 'Not yet assigned', 'fact-adjuster']);
  if (claim.closedAt) rows.push(['Closed', formatDateTime(claim.closedAt)]);
  return (
    <Card>
      <CardHeader title="Summary" />
      <dl className="divide-y divide-line">
        {rows.map(([k, v, testId]) => (
          <div key={k} className="flex justify-between gap-4 px-5 py-2.5 text-sm">
            <dt className="text-ink-faint">{k}</dt>
            <dd className="text-right text-ink tabular" data-testid={testId}>{v}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

export function SnapshotCard({ claim }: { claim: ClaimDetail }) {
  const s = claim.policySnapshot;
  if (!s) return null;
  return (
    <Card data-testid="policy-snapshot">
      <CardHeader title="Policy snapshot" />
      <div className="px-5 py-4 text-sm">
        <p className="text-ink-muted">Terms copied from policy {s.policyNumber} when the claim was submitted.</p>
        <dl className="mt-3 grid grid-cols-2 gap-3">
          <div><dt className="text-ink-faint">Coverage limit</dt><dd className="tabular">{formatMoney(s.coverageLimit)}</dd></div>
          <div><dt className="text-ink-faint">Deductible</dt><dd className="tabular">{formatMoney(s.deductible)}</dd></div>
          <div className="col-span-2"><dt className="text-ink-faint">Policy period</dt>
            <dd>{formatDate(s.effectiveDate)} – {formatDate(s.expirationDate)}</dd></div>
        </dl>
      </div>
    </Card>
  );
}

function FindingItem({ finding }: { finding: Finding }) {
  return (
    <li className="flex flex-col gap-1.5 px-5 py-3" data-testid="finding" data-rule={finding.ruleCode}>
      <div className="flex flex-wrap items-center gap-2">
        <Pill tone={finding.severity === 'WARNING' ? 'warning' : 'info'}
          label={finding.severity === 'WARNING' ? 'Needs review' : 'For information'} />
        <span className="text-sm font-medium text-ink">{finding.shortCode} · {finding.ruleTitle}</span>
        <span className="text-xs text-ink-faint">v{finding.ruleVersion}</span>
      </div>
      <p className="text-sm text-ink-muted">{finding.explanation}</p>
      {finding.relatedClaim && (
        <p className="text-sm">
          Possible match: <Link className="text-accent underline" to={`/claims/${finding.relatedClaim.id}`}>
            {finding.relatedClaim.reference}</Link>
        </p>
      )}
    </li>
  );
}

export function FindingsCard({ claim }: { claim: ClaimDetail }) {
  const findings = useFindings(claim.id, claim.status !== 'DRAFT');
  const rules = useRules();
  if (claim.status === 'DRAFT') return null;
  return (
    <Card data-testid="findings-card">
      <CardHeader title="Automated checks" />
      {findings.isPending && <SkeletonRows rows={2} />}
      {findings.isError && <div className="p-5"><Alert tone="error">Checks could not be loaded.</Alert></div>}
      {findings.data && findings.data.length === 0 && (
        <p className="px-5 py-4 text-sm text-ink-muted" data-testid="no-findings">
          No findings. All automated checks passed for this claim.
        </p>
      )}
      {findings.data && findings.data.length > 0 && (
        <ul className="divide-y divide-line">{findings.data.map((f) => <FindingItem key={f.id} finding={f} />)}</ul>
      )}
      <details className="border-t border-line px-5 py-3 text-sm">
        <summary className="cursor-pointer text-accent">How these checks work</summary>
        <p className="mt-2 text-ink-muted">
          Checks are deterministic rules applied on submission and after each policyholder response. Findings never
          reject a claim on their own; an adjuster reviews them and records a decision.
          {claim.lastValidatedAt && <> Last run {formatDateTime(claim.lastValidatedAt)}.</>}
        </p>
        <ul className="mt-2 flex flex-col gap-1.5">
          {rules.data?.map((r) => (
            <li key={r.code}><span className="font-medium">{r.shortCode} {r.title}</span>
              <span className="text-ink-muted"> — {r.description}</span></li>
          ))}
        </ul>
      </details>
    </Card>
  );
}

function statusChange(e: TimelineEvent): string | null {
  if (!e.toStatus) return null;
  return e.fromStatus
    ? `${claimStatusLabel[e.fromStatus]} → ${claimStatusLabel[e.toStatus]}`
    : claimStatusLabel[e.toStatus];
}

export function TimelineCard({ claimId }: { claimId: string }) {
  const timeline = useTimeline(claimId);
  return (
    <Card data-testid="timeline-card">
      <CardHeader title="History" />
      {timeline.isPending && <SkeletonRows rows={3} />}
      {timeline.isError && <div className="p-5"><Alert tone="error">History could not be loaded.</Alert></div>}
      {timeline.data && timeline.data.length === 0 && <EmptyState title="No activity yet" />}
      {timeline.data && timeline.data.length > 0 && (
        <ol className="relative flex flex-col gap-0 px-5 py-4">
          {timeline.data.map((e) => (
            <li key={e.id} className="relative border-l border-line pb-4 pl-5 last:pb-0" data-testid="timeline-event"
              data-event-type={e.type}>
              <span aria-hidden="true" className="absolute -left-[5px] top-1.5 h-2.5 w-2.5 rounded-full border-2
                border-surface bg-accent" />
              <p className="text-sm text-ink">{e.summary}</p>
              <p className="mt-0.5 text-xs text-ink-faint">
                {formatDateTime(e.occurredAt)} · {e.actor?.displayName ?? 'System'}
                {statusChange(e) && <> · <span className="font-medium text-ink-muted">{statusChange(e)}</span></>}
                {e.staffOnly && <> · <span className="font-medium">Staff only</span></>}
              </p>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
