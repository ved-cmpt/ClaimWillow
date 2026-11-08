import { useState } from 'react';
import { ApiRequestError } from '../../api/client';
import type { ClaimDetail } from '../../api/claims';
import { useAddNote, useNotes } from '../../api/staff';
import type { NoteType } from '../../api/staff';
import { Pill } from '../../components/StatusPill';
import { useToast } from '../../components/Toast';
import { Alert, Button, Card, CardHeader, SkeletonRows, TextArea } from '../../components/ui';
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
    <Card data-testid="notes-card">
      <CardHeader title="Notes" />
      {notes.isPending && <SkeletonRows rows={2} />}
      {notes.isError && <div className="p-5"><Alert tone="error">Notes could not be loaded.</Alert></div>}
      {notes.data && notes.data.length === 0 && <p className="px-5 py-4 text-sm text-ink-muted">No notes yet.</p>}
      {notes.data && notes.data.length > 0 && (
        <ul className="divide-y divide-line">
          {notes.data.map((n) => (
            <li key={n.id} className="flex flex-col gap-1.5 px-5 py-3" data-testid="note" data-note-type={n.type}>
              <div className="flex flex-wrap items-center gap-2 text-xs text-ink-faint">
                <Pill tone={noteLabel[n.type].tone} label={noteLabel[n.type].label} />
                <span>{n.authorName ?? 'Unknown'} · {formatDateTime(n.createdAt)}</span>
              </div>
              <p className="whitespace-pre-wrap text-sm text-ink">{n.body}</p>
            </li>
          ))}
        </ul>
      )}
      {canAdd && (
        <div className="flex flex-col gap-2 border-t border-line px-5 py-4">
          <TextArea label="Add an internal note" value={body} onChange={(e) => setBody(e.target.value)}
            hint="Internal notes are never shown to the policyholder." error={error} maxLength={4000}
            data-testid="note-body" />
          <Button variant="secondary" className="self-end" onClick={submit} busy={add.isPending}
            data-testid="add-note">Add note</Button>
        </div>
      )}
    </Card>
  );
}
