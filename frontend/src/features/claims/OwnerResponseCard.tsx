import { useState } from 'react';
import { ApiRequestError } from '../../api/client';
import type { ClaimDetail } from '../../api/claims';
import { useRespond } from '../../api/staff';
import { useToast } from '../../components/Toast';
import { Alert, Button, Callout, Card, CardHeader, TextArea } from '../../components/ui';
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
    <Card className="border-warning/30" data-testid="response-card">
      <CardHeader title="The adjuster needs more information" icon="message" />
      <div className="flex flex-col gap-4 p-4 sm:p-5">
        {claim.openRequest && (
          <Callout tone="warning" icon="message" testId="open-request"
            title={<span className="whitespace-pre-wrap font-normal text-ink">{claim.openRequest.message}</span>}>
            <span className="text-xs">
              {claim.openRequest.requestedBy} · {formatDateTime(claim.openRequest.requestedAt)}
            </span>
          </Callout>
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
