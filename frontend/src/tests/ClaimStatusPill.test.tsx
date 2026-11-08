import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ClaimStatusPill, claimStatusLabel } from '../components/ClaimStatusPill';
import { CLAIM_STATUSES } from '../api/claims';

describe('ClaimStatusPill', () => {
  it('renders a readable text label for every status (never color alone)', () => {
    CLAIM_STATUSES.forEach((status) => {
      const { unmount } = render(<ClaimStatusPill status={status} />);
      expect(screen.getByTestId('claim-status')).toHaveTextContent(claimStatusLabel[status]);
      unmount();
    });
    expect(claimStatusLabel.NEEDS_INFO).toBe('Needs info');
    expect(claimStatusLabel.IN_REVIEW).toBe('In review');
  });
});
