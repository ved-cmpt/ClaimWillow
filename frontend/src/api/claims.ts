import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, toQuery } from './client';

export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'NEEDS_INFO' | 'APPROVED' | 'REJECTED' | 'CLOSED';
export type IncidentType = 'DAMAGE' | 'LOSS';
export type ClaimAction = 'EDIT_DRAFT' | 'SUBMIT' | 'RESPOND' | 'UPLOAD_ATTACHMENT' | 'ASSIGN_TO_ME'
  | 'START_REVIEW' | 'REQUEST_INFO' | 'DECIDE' | 'CLOSE' | 'ADD_NOTE';

export const CLAIM_STATUSES: ClaimStatus[] = ['DRAFT', 'SUBMITTED', 'IN_REVIEW', 'NEEDS_INFO', 'APPROVED',
  'REJECTED', 'CLOSED'];

export interface UserSummary {
  id: string;
  displayName: string;
  role: 'POLICYHOLDER' | 'ADJUSTER' | 'ADMIN';
}

export interface ClaimSummary {
  id: string;
  reference: string | null;
  title: string | null;
  status: ClaimStatus;
  incidentType: IncidentType | null;
  incidentDate: string | null;
  estimatedLoss: number | null;
  currency: 'CAD';
  policyNumber: string | null;
  owner: UserSummary | null;
  assignedAdjuster: UserSummary | null;
  createdAt: string;
  submittedAt: string | null;
  updatedAt: string;
}

export interface PolicySnapshot {
  policyNumber: string;
  coverageLimit: number;
  deductible: number;
  effectiveDate: string;
  expirationDate: string;
  takenAt: string;
}

export interface OpenRequest {
  message: string;
  requestedBy: string;
  requestedAt: string;
}

export interface ClaimDetail {
  id: string;
  reference: string | null;
  title: string | null;
  description: string | null;
  status: ClaimStatus;
  incidentType: IncidentType | null;
  incidentDate: string | null;
  estimatedLoss: number | null;
  currency: 'CAD';
  policy: { id: string; policyNumber: string } | null;
  policySnapshot: PolicySnapshot | null;
  owner: UserSummary | null;
  assignedAdjuster: UserSummary | null;
  version: number;
  createdAt: string;
  submittedAt: string | null;
  updatedAt: string;
  decidedAt: string | null;
  closedAt: string | null;
  lastValidatedAt: string | null;
  availableActions: ClaimAction[];
  openRequest: OpenRequest | null;
}

export interface Finding {
  id: string;
  ruleCode: string;
  shortCode: string;
  ruleTitle: string;
  ruleVersion: number;
  severity: 'INFO' | 'WARNING';
  explanation: string;
  relatedClaim: { id: string; reference: string } | null;
  checkedAt: string;
}

export interface TimelineEvent {
  id: string;
  type: string;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus | null;
  summary: string;
  actor: UserSummary | null;
  staffOnly: boolean;
  occurredAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StatusCounts {
  counts: { status: ClaimStatus; count: number }[];
  total: number;
}

export interface RuleInfo {
  code: string;
  shortCode: string;
  version: number;
  title: string;
  description: string;
  blocking: boolean;
}

export interface DraftInput {
  policyId: string | null;
  incidentDate: string | null;
  incidentType: IncidentType | null;
  title: string | null;
  description: string | null;
  estimatedLoss: string | null;
  version?: number;
}

export const claimKeys = {
  all: ['claims'] as const,
  mine: (params: object) => ['claims', 'mine', params] as const,
  mineSummary: ['claims', 'mine', 'summary'] as const,
  detail: (id: string) => ['claims', 'detail', id] as const,
  findings: (id: string) => ['claims', 'detail', id, 'findings'] as const,
  timeline: (id: string) => ['claims', 'detail', id, 'timeline'] as const,
};

export function useMyClaims(params: { status?: string; query?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: claimKeys.mine(params),
    queryFn: ({ signal }) => api.get<PageResponse<ClaimSummary>>(`/claims/mine${toQuery(params)}`, signal),
    placeholderData: (previous) => previous,
  });
}

export function useMyClaimSummary() {
  return useQuery({
    queryKey: claimKeys.mineSummary,
    queryFn: ({ signal }) => api.get<StatusCounts>('/claims/mine/summary', signal),
  });
}

export function useClaim(id: string | undefined) {
  return useQuery({
    queryKey: claimKeys.detail(id ?? 'none'),
    queryFn: ({ signal }) => api.get<ClaimDetail>(`/claims/${id}`, signal),
    enabled: Boolean(id),
    retry: false,
  });
}

export function useFindings(id: string | undefined, enabled = true) {
  return useQuery({
    queryKey: claimKeys.findings(id ?? 'none'),
    queryFn: ({ signal }) => api.get<Finding[]>(`/claims/${id}/findings`, signal),
    enabled: Boolean(id) && enabled,
  });
}

export function useTimeline(id: string | undefined) {
  return useQuery({
    queryKey: claimKeys.timeline(id ?? 'none'),
    queryFn: ({ signal }) => api.get<TimelineEvent[]>(`/claims/${id}/timeline`, signal),
    enabled: Boolean(id),
  });
}

export function useRules() {
  return useQuery({
    queryKey: ['rules'],
    queryFn: ({ signal }) => api.get<RuleInfo[]>('/rules', signal),
    staleTime: Infinity,
  });
}

/** Invalidates every cached view of claims after a mutation. */
export function useInvalidateClaims() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: claimKeys.all });
}

export function useSaveDraft() {
  const invalidate = useInvalidateClaims();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: DraftInput }) =>
      id ? api.patch<ClaimDetail>(`/claims/${id}/draft`, input) : api.post<ClaimDetail>('/claims', input),
    onSuccess: () => invalidate(),
  });
}

export function useSubmitClaim() {
  const invalidate = useInvalidateClaims();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) =>
      api.post<ClaimDetail>(`/claims/${id}/submit`, { version }),
    onSuccess: () => invalidate(),
  });
}
