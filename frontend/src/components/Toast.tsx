import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';

type ToastTone = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastApi {
  show: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastApi>({ show: () => undefined });

const toneClass: Record<ToastTone, string> = {
  success: 'border-success/40 bg-success-soft text-success',
  error: 'border-danger/40 bg-danger-soft text-danger',
  info: 'border-info/40 bg-info-soft text-info',
};

/** Lightweight accessible toasts announced through a polite live region. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => setItems((all) => all.filter((t) => t.id !== id)), []);

  const show = useCallback((message: string, tone: ToastTone = 'success') => {
    const id = nextId.current++;
    setItems((all) => [...all, { id, tone, message }]);
    window.setTimeout(() => dismiss(id), 6000);
  }, [dismiss]);

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" role="region" aria-label="Notifications"
        className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4">
        {items.map((t) => (
          <div key={t.id} data-testid="toast"
            className={`pointer-events-auto flex max-w-md items-start gap-3 rounded-md border px-4 py-3 text-sm
              shadow-md ${toneClass[t.tone]}`}>
            <span className="flex-1">{t.message}</span>
            <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss notification"
              className="text-current opacity-70 hover:opacity-100">×</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  return useContext(ToastContext);
}
