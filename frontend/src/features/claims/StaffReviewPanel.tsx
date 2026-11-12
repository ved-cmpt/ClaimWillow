import { useState } from 'react';
import { ApiRequestError } from '../../api/client';
import type { ClaimDetail } from '../../api/claims';
import { useStaffAction } from '../../api/staff';
import type { StaffAction } from '../../api/staff';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { useToast } from '../../components/Toast';
import { Alert, Button, Card, CardHeader, TextArea } from '../../components/ui';

interface Props {
  claim: ClaimDetail;
  onRefresh: () => void;
}

type Pending = { action: StaffAction; body: Record<string, unknown>; success: string } | null;

const MIN_REASON = 10;

/**
 * Staff decision panel. Renders only the actions the server says are available for this
 * viewer and status. Conflicts (409) show the server's message and a refresh option instead of
 * silently overwriting another adjuster's change.
 */
export function StaffReviewPanel({ claim, onRefresh }: Props) {
  const toast = useToast();
  const mutation = useStaffAction();
  const [error, setError] = useState<ApiRequestError | Error | null>(null);
  const [requestText, setRequestText] = useState('');
  const [requestError, setRequestError] = useState<string>();
  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED' | ''>('');
  const [decisionText, setDecisionText] = useState('');
  const [decisionError, setDecisionError] = useState<{ decision?: string; reason?: string }>({});
  const [confirm, setConfirm] = useState<Pending>(null);
  const actions = claim.availableActions;

  const run = (action: StaffAction, body: Record<string, unknown>, success: string, after?: () => void) => {
    setError(null);
    mutation.mutate({ claimId: claim.id, action, body: { version: claim.version, ...body } }, {
      onSuccess: () => {
        toast.show(success);
        after?.();
        setConfirm(null);
      },
      onError: (err) => {
        setError(err);
        setConfirm(null);
      },
    });
  };

  const submitRequest = () => {
    if (requestText.trim().length < MIN_REASON) {
      setRequestError(`Explain what you need (at least ${MIN_REASON} characters).`);
      return;
    }
    setRequestError(undefined);
    setConfirm({ action: 'request-info', body: { reason: requestText.trim() },
      success: 'Information requested. The policyholder can now respond.' });
  };

  const submitDecision = () => {
    const errs: { decision?: string; reason?: string } = {};
    if (!decision) errs.decision = 'Choose approve or reject.';
    if (decisionText.trim().length < MIN_REASON) {
      errs.reason = decision === 'REJECTED'
        ? `A rejection reason is required (at least ${MIN_REASON} characters).`
        : `A decision note is required (at least ${MIN_REASON} characters).`;
    }
    setDecisionError(errs);
    if (errs.decision || errs.reason) return;
    setConfirm({ action: 'decision', body: { decision, reason: decisionText.trim() },
      success: decision === 'APPROVED' ? 'Claim approved.' : 'Claim rejected.' });
  };

  const isConflict = error instanceof ApiRequestError && error.status === 409;
  const nothingToDo = !actions.some((a) => ['ASSIGN_TO_ME', 'START_REVIEW', 'REQUEST_INFO', 'DECIDE', 'CLOSE']
    .includes(a));

  return (
    <Card data-testid="staff-panel">
      <CardHeader title="Review actions" icon="scale" />
      <div className="flex flex-col gap-4 p-4 sm:p-5">
        {error && (
          <Alert tone={isConflict ? 'warning' : 'error'} testId="action-error"
            title={isConflict ? 'This claim changed' : 'The action failed'}>
            <p>{error.message}</p>
            {isConflict && (
              <Button variant="secondary" className="mt-2" data-testid="refresh-claim"
                onClick={() => { setError(null); onRefresh(); }}>Refresh claim</Button>
            )}
          </Alert>
        )}

        {nothingToDo && (
          <p className="text-sm text-ink-muted" data-testid="no-actions">
            {claim.status === 'CLOSED' ? 'This claim is closed. Its history is preserved below.'
              : claim.assignedAdjuster ? `Assigned to ${claim.assignedAdjuster.displayName}. Only the assigned `
                + 'adjuster can act on it.' : 'Waiting on the policyholder.'}
          </p>
        )}

        {actions.includes('ASSIGN_TO_ME') && (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-ink-muted">
              {claim.assignedAdjuster ? `Currently assigned to ${claim.assignedAdjuster.displayName}.`
                : 'This claim is not assigned yet.'}
            </p>
            <Button data-testid="action-assign" busy={mutation.isPending}
              onClick={() => run('assign', {}, 'Claim assigned to you.')}>Assign to me</Button>
          </div>
        )}

        {actions.includes('START_REVIEW') && (
          <Button data-testid="action-start-review" busy={mutation.isPending}
            onClick={() => run('start-review', {}, 'Review started.')}>Start review</Button>
        )}

        {actions.includes('REQUEST_INFO') && (
          <div className="flex flex-col gap-2 border-t border-line pt-4 first:border-0 first:pt-0">
            <h3 className="text-sm font-semibold">Request more information</h3>
            <TextArea label="What do you need from the policyholder?" required value={requestText}
              onChange={(e) => setRequestText(e.target.value)} error={requestError} maxLength={2000}
              data-testid="request-reason" />
            <Button variant="secondary" onClick={submitRequest} data-testid="action-request-info">
              Request information
            </Button>
          </div>
        )}

        {actions.includes('DECIDE') && (
          <fieldset className="flex flex-col gap-3 border-t border-line pt-4">
            <legend className="text-sm font-semibold">Record a decision</legend>
            <div role="radiogroup" aria-label="Decision" className="flex gap-4">
              {(['APPROVED', 'REJECTED'] as const).map((d) => (
                <label key={d} className="flex items-center gap-2 text-sm">
                  <input type="radio" name="decision" value={d} checked={decision === d}
                    onChange={() => setDecision(d)} data-testid={`decision-${d.toLowerCase()}`} />
                  {d === 'APPROVED' ? 'Approve' : 'Reject'}
                </label>
              ))}
            </div>
            {decisionError.decision && <p className="text-sm text-danger">{decisionError.decision}</p>}
            <TextArea label={decision === 'REJECTED' ? 'Rejection reason' : 'Decision note'} required
              value={decisionText} onChange={(e) => setDecisionText(e.target.value)} error={decisionError.reason}
              maxLength={2000} hint="Visible to the policyholder." data-testid="decision-reason" />
            <Button onClick={submitDecision} data-testid="action-decide">Record decision</Button>
          </fieldset>
        )}

        {actions.includes('CLOSE') && (
          <div className="flex flex-col gap-2 border-t border-line pt-4 first:border-0 first:pt-0">
            <p className="text-sm text-ink-muted">The decision is recorded. Close the claim to complete it.</p>
            <Button variant="secondary" data-testid="action-close"
              onClick={() => setConfirm({ action: 'close', body: {}, success: 'Claim closed.' })}>Close claim</Button>
          </div>
        )}
      </div>

      <ConfirmDialog open={confirm !== null} busy={mutation.isPending}
        tone={confirm?.action === 'decision' && decision === 'REJECTED' ? 'danger' : 'primary'}
        title={confirm?.action === 'decision' ? (decision === 'APPROVED' ? 'Approve this claim?' : 'Reject this claim?')
          : confirm?.action === 'close' ? 'Close this claim?' : 'Send this request?'}
        confirmLabel={confirm?.action === 'decision' ? 'Record decision' : confirm?.action === 'close' ? 'Close claim'
          : 'Send request'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => confirm && run(confirm.action, confirm.body, confirm.success, () => {
          if (confirm.action === 'request-info') setRequestText('');
          if (confirm.action === 'decision') { setDecisionText(''); setDecision(''); }
        })}>
        {confirm?.action === 'decision' && 'Decisions are final and visible to the policyholder.'}
        {confirm?.action === 'close' && 'Closed claims can no longer be changed.'}
        {confirm?.action === 'request-info' && 'The claim moves to Needs info until the policyholder responds.'}
      </ConfirmDialog>
    </Card>
  );
}
