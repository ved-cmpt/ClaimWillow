import type { ClaimStatus } from '../api/claims';
import { Pill } from './StatusPill';

export const claimStatusLabel: Record<ClaimStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  NEEDS_INFO: 'Needs info',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CLOSED: 'Closed',
};

const tone = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  IN_REVIEW: 'accent',
  NEEDS_INFO: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CLOSED: 'muted',
} as const;

export function ClaimStatusPill({ status }: { status: ClaimStatus }) {
  return <Pill tone={tone[status]} label={claimStatusLabel[status]} testId="claim-status" />;
}
