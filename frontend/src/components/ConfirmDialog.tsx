import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';
import { Button } from './ui';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  tone?: 'primary' | 'danger';
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Accessible modal confirmation for irreversible actions (Escape cancels, focus starts on Cancel). */
export function ConfirmDialog({ open, title, children, confirmLabel, tone = 'primary', busy = false, onConfirm,
  onCancel }: ConfirmDialogProps) {
  const titleId = useId();
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink/40 px-4">
      <div role="dialog" aria-modal="true" aria-labelledby={titleId} data-testid="confirm-dialog"
        className="w-full max-w-md rounded-card border border-line bg-surface p-5 shadow-lg">
        <h2 id={titleId} className="text-lg font-semibold text-ink">{title}</h2>
        <div className="mt-2 text-sm text-ink-muted">{children}</div>
        <div className="mt-5 flex justify-end gap-3">
          <button ref={cancelRef} type="button" onClick={onCancel} disabled={busy}
            className="rounded-md border border-line-strong px-3.5 py-2 text-sm hover:bg-subtle">Cancel</button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} busy={busy}
            data-testid="confirm-action">{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}
