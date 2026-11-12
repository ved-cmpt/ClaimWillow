import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError } from '../../api/client';
import { Alert, BlockHeading, SkeletonRows } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { useToast } from '../../components/Toast';
import { formatDateTime } from '../../lib/format';

export interface Attachment {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string | null;
  createdAt: string;
  downloadUrl: string;
}

export const MAX_BYTES = 5 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg'];

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Quick client-side checks; the server re-validates size and actual file contents. */
export function precheckFile(file: File): string | null {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  if (!ALLOWED_EXTENSIONS.includes(ext)) return 'Only PDF, PNG and JPEG files can be attached.';
  if (file.size > MAX_BYTES) return 'The file exceeds the maximum size of 5 MB.';
  if (file.size === 0) return 'The file is empty.';
  return null;
}

export function useAttachments(claimId: string) {
  return useQuery({
    queryKey: ['claims', 'detail', claimId, 'attachments'],
    queryFn: ({ signal }) => api.get<Attachment[]>(`/claims/${claimId}/attachments`, signal),
  });
}

interface Props {
  claimId: string;
  canUpload: boolean;
  compact?: boolean;
}

export function EvidenceList({ claimId, canUpload, compact = false }: Props) {
  const list = useAttachments(claimId);
  const client = useQueryClient();
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<Attachment>(`/claims/${claimId}/attachments`, form);
    },
    onSuccess: (a) => {
      toast.show(`${a.filename} attached.`);
      void client.invalidateQueries({ queryKey: ['claims', 'detail', claimId] });
    },
    onError: (e) => setError(e instanceof ApiRequestError ? e.message : 'The file could not be uploaded.'),
  });

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    setError(null);
    if (!file) return;
    const problem = precheckFile(file);
    if (problem) {
      setError(problem);
      return;
    }
    upload.mutate(file);
  };

  return (
    <div className="flex flex-col gap-3" data-testid="evidence">
      {list.isPending && <SkeletonRows rows={1} />}
      {list.isError && <Alert tone="error">Attachments could not be loaded.</Alert>}
      {list.data && list.data.length === 0 && (
        <p className="text-sm text-ink-muted" data-testid="no-evidence">No files attached yet.</p>
      )}
      {list.data && list.data.length > 0 && (
        <ul className="divide-y divide-line rounded-md border border-line bg-surface">
          {list.data.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm"
              data-testid="attachment">
              <div className="flex min-w-0 items-start gap-2.5">
                <Icon name="file" size={16} className="mt-0.5 text-ink-faint" />
                <div className="min-w-0">
                <a href={a.downloadUrl} download className="break-all font-medium text-accent hover:underline"
                  data-testid="attachment-link">{a.filename}</a>
                {!compact && (
                  <p className="text-xs text-ink-faint">
                    {formatSize(a.sizeBytes)} · {a.uploadedBy ?? 'Unknown'} · {formatDateTime(a.createdAt)}
                  </p>
                )}
                </div>
              </div>
              <span className="rounded bg-subtle px-1.5 text-[11px] font-medium uppercase text-ink-muted">{a.contentType.split('/')[1]}</span>
            </li>
          ))}
        </ul>
      )}
      {error && <Alert tone="error" testId="upload-error">{error}</Alert>}
      {canUpload && (
        <div className="flex flex-wrap items-center gap-3">
          <label className={`inline-flex cursor-pointer items-center gap-2 rounded-md border border-line-strong
            bg-surface px-3 py-1.5 text-sm font-medium text-ink shadow-soft hover:bg-subtle focus-within:ring-2
            focus-within:ring-accent ${upload.isPending ? 'opacity-60' : ''}`}>
            <Icon name="paperclip" size={15} />
            {upload.isPending ? 'Uploading…' : 'Attach a file'}
            <input ref={inputRef} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
              className="sr-only" onChange={onChange} disabled={upload.isPending} data-testid="evidence-input" />
          </label>
          <span className="text-xs text-ink-faint">PDF, PNG or JPEG, up to 5 MB. Synthetic test files only.</span>
        </div>
      )}
    </div>
  );
}

export function EvidenceCard(props: Props) {
  return (
    <section data-testid="evidence-card" aria-labelledby="evidence-heading">
      <BlockHeading id="evidence-heading" title="Evidence" icon="paperclip" />
      <EvidenceList {...props} />
    </section>
  );
}
