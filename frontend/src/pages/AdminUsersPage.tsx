import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError } from '../api/client';
import type { Role } from '../api/types';
import { Pill } from '../components/StatusPill';
import { useToast } from '../components/Toast';
import { Avatar } from '../components/AppShell';
import { rowClass, TableFrame, theadClass } from '../components/Filters';
import { Alert, Button, PageHeader, SkeletonRows, Th } from '../components/ui';
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
      <PageHeader title="Accounts" icon="users" subtitle="Synthetic demo accounts. Disabled accounts cannot sign in." />
      <TableFrame>
        {users.isPending && <SkeletonRows rows={6} />}
        {users.isError && <div className="p-4"><Alert tone="error">Accounts could not be loaded.</Alert></div>}
        {users.data && (
          <table className="w-full text-sm" data-testid="accounts-table">
            <thead className={`hidden md:table-header-group ${theadClass}`}>
              <tr>
                <Th icon="user" className="pl-5">Name</Th>
                <Th icon="shield">Role</Th>
                <Th icon="tag">Status</Th>
                <Th icon="clock">Created</Th>
                <Th className="pr-5"><span className="sr-only">Action</span></Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {users.data.map((u) => (
                <tr key={u.id} className={`grid grid-cols-2 gap-2 px-4 py-3 md:table-row md:px-0 ${rowClass}`}>
                  <td className="col-span-2 md:py-2.5 md:pl-5 md:pr-3">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={u.displayName} />
                      <div className="min-w-0">
                        <p className="font-medium">{u.displayName}</p>
                        <p className="break-all text-xs text-ink-faint">{u.username} · {u.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="md:px-3 md:py-2.5">{roleLabel[u.role]}</td>
                  <td className="md:px-3 md:py-2.5">
                    <Pill tone={u.enabled ? 'success' : 'muted'} label={u.enabled ? 'Active' : 'Disabled'} />
                  </td>
                  <td className="text-xs text-ink-faint md:px-3 md:py-2.5">{formatDateTime(u.createdAt)}</td>
                  <td className="text-right md:py-2.5 md:pl-3 md:pr-5">
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
      </TableFrame>
    </>
  );
}
