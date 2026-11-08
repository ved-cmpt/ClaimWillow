import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError } from '../api/client';
import type { Role } from '../api/types';
import { Pill } from '../components/StatusPill';
import { useToast } from '../components/Toast';
import { Alert, Button, Card, PageHeader, SkeletonRows } from '../components/ui';
import { useCurrentUser } from '../features/auth/useAuth';
import { formatDateTime } from '../lib/format';

interface UserRow {
  id: string;
  username: string;
  email: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

const roleLabel: Record<Role, string> = { POLICYHOLDER: 'Policyholder', ADJUSTER: 'Adjuster', ADMIN: 'Admin' };

export function AdminUsersPage() {
  const me = useCurrentUser();
  const client = useQueryClient();
  const toast = useToast();
  const users = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: ({ signal }) => api.get<UserRow[]>('/admin/users', signal),
  });
  const toggle = useMutation({
    mutationFn: (u: UserRow) => api.patch<UserRow>(`/admin/users/${u.id}`, { enabled: !u.enabled }),
    onSuccess: (u) => {
      toast.show(`${u.displayName} ${u.enabled ? 'enabled' : 'disabled'}.`);
      void client.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
    onError: (e) => toast.show(e instanceof ApiRequestError ? e.message : 'The change failed.', 'error'),
  });

  return (
    <>
      <PageHeader title="Accounts" subtitle="Synthetic demo accounts. Disabled accounts cannot sign in." />
      <Card>
        {users.isPending && <SkeletonRows rows={6} />}
        {users.isError && <div className="p-5"><Alert tone="error">Accounts could not be loaded.</Alert></div>}
        {users.data && (
          <table className="w-full text-sm" data-testid="accounts-table">
            <thead className="hidden border-b border-line bg-subtle text-left text-xs uppercase tracking-wide
              text-ink-faint md:table-header-group">
              <tr>
                <th scope="col" className="px-5 py-2.5 font-medium">Name</th>
                <th scope="col" className="px-3 py-2.5 font-medium">Role</th>
                <th scope="col" className="px-3 py-2.5 font-medium">Status</th>
                <th scope="col" className="px-3 py-2.5 font-medium">Created</th>
                <th scope="col" className="px-5 py-2.5"><span className="sr-only">Action</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {users.data.map((u) => (
                <tr key={u.id} className="grid grid-cols-2 gap-2 px-5 py-3 md:table-row md:px-0">
                  <td className="col-span-2 md:px-5 md:py-3">
                    <p className="font-medium">{u.displayName}</p>
                    <p className="text-xs text-ink-faint">{u.username} · {u.email}</p>
                  </td>
                  <td className="md:px-3 md:py-3">{roleLabel[u.role]}</td>
                  <td className="md:px-3 md:py-3">
                    <Pill tone={u.enabled ? 'success' : 'muted'} label={u.enabled ? 'Active' : 'Disabled'} />
                  </td>
                  <td className="text-xs text-ink-faint md:px-3 md:py-3">{formatDateTime(u.createdAt)}</td>
                  <td className="text-right md:px-5 md:py-3">
                    {u.id !== me.data?.id && (
                      <Button variant="secondary" onClick={() => toggle.mutate(u)}
                        busy={toggle.isPending && toggle.variables?.id === u.id}>
                        {u.enabled ? 'Disable' : 'Enable'}<span className="sr-only"> {u.displayName}</span>
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}
