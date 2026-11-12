import { useState } from 'react';
import { ApiRequestError } from '../../api/client';
import type { ClaimDetail } from '../../api/claims';
import { useAddNote, useNotes } from '../../api/staff';
import type { NoteType } from '../../api/staff';
import { Pill } from '../../components/StatusPill';
import { useToast } from '../../components/Toast';
import { Avatar } from '../../components/AppShell';
import { Alert, BlockHeading, Button, SkeletonRows, TextArea } from '../../components/ui';
import { formatDateTime } from '../../lib/format';

const noteLabel: Record<NoteType, { label: string; tone: 'neutral' | 'warning' | 'info' | 'accent' }> = {
  STAFF_INTERNAL: { label: 'Internal (staff only)', tone: 'neutral' },
  REQUEST_INFO: { label: 'Information request', tone: 'warning' },
  POLICYHOLDER_RESPONSE: { label: 'Policyholder response', tone: 'info' },
  DECISION: { label: 'Decision', tone: 'accent' },
};

export function NotesCard({ claim, staff }: { claim: ClaimDetail; staff: boolean }) {
  const notes = useNotes(claim.id);
  const add = useAddNote();
  const toast = useToast();
  const [body, setBody] = useState('');
  const [error, setError] = useState<string>();
  const canAdd = staff && claim.availableActions.includes('ADD_NOTE');

  const submit = () => {
    if (!body.trim()) {
      setError('Write a note first.');
      return;
    }
    setError(undefined);
    add.mutate({ claimId: claim.id, body: body.trim() }, {
      onSuccess: () => { setBody(''); toast.show('Internal note added.'); },
      onError: (e) => setError(e instanceof ApiRequestError ? e.message : 'The note could not be saved.'),
    });
  };

  return (
    <section data-testid="notes-card" aria-labelledby="notes-heading">
      <BlockHeading id="notes-heading" title="Notes" icon="message" />
      {notes.isPending && <SkeletonRows rows={2} />}
      {notes.isError && <Alert tone="error">Notes could not be loaded.</Alert>}
      {notes.data && notes.data.length === 0 && <p className="text-sm text-ink-muted">No notes yet.</p>}
      {notes.data && notes.data.length > 0 && (
        <ul className="flex flex-col gap-2">
          {notes.data.map((n) => (
            <li key={n.id} className="flex gap-3 rounded-md border border-line bg-surface px-3 py-2.5 sm:px-4"
              data-testid="note" data-note-type={n.type}>
              <Avatar name={n.authorName ?? '?'} size="sm" />
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2 text-xs text-ink-faint">
                  <span className="text-sm font-medium text-ink">{n.authorName ?? 'Unknown'}</span>
                  <span>{formatDateTime(n.createdAt)}</span>
                  <Pill tone={noteLabel[n.type].tone} label={noteLabel[n.type].label} />
                </div>
                <p className="whitespace-pre-wrap text-sm text-ink">{n.body}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {canAdd && (
        <div className="mt-3 flex flex-col gap-2">
          <TextArea label="Add an internal note" value={body} onChange={(e) => setBody(e.target.value)}
            hint="Internal notes are never shown to the policyholder." error={error} maxLength={4000}
            data-testid="note-body" />
          <Button variant="secondary" className="self-end" onClick={submit} busy={add.isPending}
            data-testid="add-note">Add note</Button>
        </div>
      )}
    </section>
  );
}
