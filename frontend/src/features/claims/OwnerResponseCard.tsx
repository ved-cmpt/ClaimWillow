import { useState } from 'react';
import { ApiRequestError } from '../../api/client';
import type { ClaimDetail } from '../../api/claims';
import { useRespond } from '../../api/staff';
import { useToast } from '../../components/Toast';
import { Alert, Button, Card, CardHeader, TextArea } from '../../components/ui';
import { formatDateTime } from '../../lib/format';

/** Shown to the owner while the claim is NEEDS_INFO: the adjuster's request and a reply form. */
export function OwnerResponseCard({ claim, onRefresh, evidence }: { claim: ClaimDetail; onRefresh: () => void;
  evidence?: React.ReactNode }) {
  const respond = useRespond();
  const toast = useToast();
  const [message, setMessage] = useState('');
  const [error, setError] = useState<ApiRequestError | null>(null);

  if (!claim.availableActions.includes('RESPOND')) return null;

  const submit = () => {
    setError(null);
    respond.mutate({ claimId: claim.id, version: claim.version, message }, {
      onSuccess: () => { setMessage(''); toast.show('Response sent. The adjuster will continue the review.'); },
      onError: (e) => setError(e instanceof ApiRequestError ? e : new ApiRequestError(0, 'ERROR', 'The response could not be sent.')),
    });
  };

  return (
    <Card className="border-warning/40" data-testid="response-card">
      <CardHeader title="The adjuster needs more information" />
      <div className="flex flex-col gap-4 p-5">
        {claim.openRequest && (
          <blockquote className="rounded-md border-l-4 border-warning bg-warning-soft px-4 py-3 text-sm text-ink"
            data-testid="open-request">
            <p className="whitespace-pre-wrap">{claim.openRequest.message}</p>
            <footer className="mt-1 text-xs text-ink-muted">
              {claim.openRequest.requestedBy} · {formatDateTime(claim.openRequest.requestedAt)}
            </footer>
          </blockquote>
        )}
        {evidence}
        {error && (
          <Alert tone={error.status === 409 ? 'warning' : 'error'} testId="response-error">
            <p>{error.message}</p>
            {error.status === 409 && <Button variant="secondary" className="mt-2" onClick={onRefresh}>Refresh</Button>}
          </Alert>
        )}
        <TextArea label="Your response" value={message} onChange={(e) => setMessage(e.target.value)} maxLength={4000}
          hint="Explain or add details. You can also attach files above; a written note is optional if you do."
          error={error?.fieldErrors.find((f) => f.field === 'message')?.message} data-testid="response-message" />
        <Button onClick={submit} busy={respond.isPending} className="self-end" data-testid="send-response">
          Send response
        </Button>
      </div>
    </Card>
  );
}
