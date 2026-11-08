import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError } from '../../api/client';
import type { CurrentUser } from '../../api/types';

export const ME_KEY = ['auth', 'me'] as const;

async function fetchMe(signal?: AbortSignal): Promise<CurrentUser | null> {
  try {
    return await api.get<CurrentUser>('/auth/me', signal);
  } catch (err) {
    if (err instanceof ApiRequestError && err.status === 401) return null;
    throw err;
  }
}

export function useCurrentUser() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: ({ signal }) => fetchMe(signal),
    staleTime: 60_000,
    retry: false,
  });
}

export function useLogin() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (credentials: { username: string; password: string }) =>
      api.post<CurrentUser>('/auth/login', credentials),
    onSuccess: (user) => {
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'auth' });
      client.setQueryData(ME_KEY, user);
    },
  });
}

export function useLogout() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => {
      client.clear();
      client.setQueryData(ME_KEY, null);
    },
  });
}

export function homePathFor(user: CurrentUser): string {
  return user.role === 'POLICYHOLDER' ? '/claims' : '/staff/queue';
}
