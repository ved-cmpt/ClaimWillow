import { useMutation, useQuery } from '@tanstack/react-query';
import { api, toQuery } from './client';
import { useInvalidateClaims } from './claims';
import type { ClaimDetail, ClaimSummary, PageResponse } from './claims';

export interface DashboardSummary {
  newClaims: number;
  inReview: number;
  needsInfo: number;
  assignedToMe: number;
  unassigned: number;
}

export interface AdjusterOption {
  id: string;
  displayName: string;
}

export type NoteType = 'STAFF_INTERNAL' | 'REQUEST_INFO' | 'POLICYHOLDER_RESPONSE' | 'DECISION';

export interface Note {
  id: string;
  type: NoteType;
  body: string;
  authorId: string;
  authorName: string | null;
  authorRole: 'POLICYHOLDER' | 'ADJUSTER' | 'ADMIN' | null;
  createdAt: string;
}

export interface QueueParams {
  status?: string;
  assignedTo?: string;
  query?: string;
  submittedFrom?: string;
  submittedTo?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export const OPEN_STATUSES = 'SUBMITTED,IN_REVIEW,NEEDS_INFO';

function queueQuery(p: QueueParams): string {
  const { status, ...rest } = p;
  const base = toQuery(rest);
  if (!status) return base;
  const statuses = status.split(',').map((s) => `status=${encodeURIComponent(s)}`).join('&');
  return base ? `${base}&${statuses}` : `?${statuses}`;
}

export function useQueue(params: QueueParams) {
  return useQuery({
    queryKey: ['claims', 'queue', params],
    queryFn: ({ signal }) => api.get<PageResponse<ClaimSummary>>(`/staff/claims${queueQuery(params)}`, signal),
    placeholderData: (previous) => previous,
  });
}

export function useDashboard() {
  return useQuery({
    queryKey: ['claims', 'dashboard'],
    queryFn: ({ signal }) => api.get<DashboardSummary>('/staff/dashboard/summary', signal),
  });
}

export function useAdjusters() {
  return useQuery({
    queryKey: ['staff', 'adjusters'],
    queryFn: ({ signal }) => api.get<AdjusterOption[]>('/staff/adjusters', signal),
    staleTime: 5 * 60_000,
  });
}

export function useNotes(claimId: string) {
  return useQuery({
    queryKey: ['claims', 'detail', claimId, 'notes'],
    queryFn: ({ signal }) => api.get<Note[]>(`/claims/${claimId}/notes`, signal),
  });
}

export type StaffAction = 'assign' | 'start-review' | 'request-info' | 'decision' | 'close';

export function useStaffAction() {
  const invalidate = useInvalidateClaims();
  return useMutation({
    mutationFn: ({ claimId, action, body }: { claimId: string; action: StaffAction; body: Record<string, unknown> }) =>
      api.post<ClaimDetail>(`/staff/claims/${claimId}/${action}`, body),
    onSettled: () => invalidate(),
  });
}

export function useAddNote() {
  const invalidate = useInvalidateClaims();
  return useMutation({
    mutationFn: ({ claimId, body }: { claimId: string; body: string }) =>
      api.post<Note>(`/staff/claims/${claimId}/notes`, { body }),
    onSuccess: () => invalidate(),
  });
}

export function useRespond() {
  const invalidate = useInvalidateClaims();
  return useMutation({
    mutationFn: ({ claimId, version, message }: { claimId: string; version: number; message: string }) =>
      api.post<ClaimDetail>(`/claims/${claimId}/responses`, { version, message }),
    onSettled: () => invalidate(),
  });
}
