import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Spinner } from '../components/Spinner';

interface SystemStatus {
  name: string;
  status: string;
  serverTime: string;
}

export function StatusPage() {
  const status = useQuery({
    queryKey: ['system-status'],
    queryFn: ({ signal }) => api.get<SystemStatus>('/system/status', signal),
  });
  return (
    <section className="rounded-card border border-line bg-surface p-6">
      <h1 className="text-xl font-semibold">Service status</h1>
      <div className="mt-4" data-testid="service-status">
        {status.isPending && <Spinner label="Checking backend" />}
        {status.isError && <p className="text-danger">Backend is unreachable.</p>}
        {status.data && (
          <p>
            Backend <strong>{status.data.status}</strong> at{' '}
            {new Date(status.data.serverTime).toLocaleString()}
          </p>
        )}
      </div>
    </section>
  );
}
