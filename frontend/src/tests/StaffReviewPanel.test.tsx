import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StaffReviewPanel } from '../features/claims/StaffReviewPanel';
import { OwnerResponseCard } from '../features/claims/OwnerResponseCard';
import type { ClaimAction, ClaimDetail } from '../api/claims';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

function claim(status: ClaimDetail['status'], actions: ClaimAction[], extra: Partial<ClaimDetail> = {}): ClaimDetail {
  return {
    id: 'c1', reference: 'CW-2026-ABC234', title: 'Burst pipe', description: 'd', status, incidentType: 'DAMAGE',
    incidentDate: '2026-03-01', estimatedLoss: 1500, currency: 'CAD', policy: null, policySnapshot: null,
    owner: { id: 'o1', displayName: 'Priya Shah', role: 'POLICYHOLDER' }, assignedAdjuster: null, version: 4,
    createdAt: '', submittedAt: '', updatedAt: '', decidedAt: null, closedAt: null, lastValidatedAt: null,
    availableActions: actions, openRequest: null, ...extra,
  };
}

describe('StaffReviewPanel', () => {
  it('shows only the actions the server allows', () => {
    const { unmount } = renderWithProviders(
      <StaffReviewPanel claim={claim('SUBMITTED', ['ASSIGN_TO_ME', 'ADD_NOTE'])} onRefresh={() => undefined} />);
    expect(screen.getByTestId('action-assign')).toBeInTheDocument();
    expect(screen.queryByTestId('action-decide')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-request-info')).not.toBeInTheDocument();
    unmount();

    renderWithProviders(<StaffReviewPanel claim={claim('IN_REVIEW', ['REQUEST_INFO', 'DECIDE', 'ADD_NOTE'],
      { assignedAdjuster: { id: 'a1', displayName: 'Dana Okafor', role: 'ADJUSTER' } })} onRefresh={() => undefined} />);
    expect(screen.queryByTestId('action-assign')).not.toBeInTheDocument();
    expect(screen.getByTestId('action-decide')).toBeInTheDocument();
    expect(screen.getByTestId('action-request-info')).toBeInTheDocument();
  });

  it('explains why a non-assignee has nothing to do', () => {
    renderWithProviders(<StaffReviewPanel claim={claim('IN_REVIEW', ['ADD_NOTE'],
      { assignedAdjuster: { id: 'a2', displayName: 'Sam Lindqvist', role: 'ADJUSTER' } })} onRefresh={() => undefined} />);
    expect(screen.getByTestId('no-actions')).toHaveTextContent('Assigned to Sam Lindqvist');
  });

  it('requires a reason before requesting information and a note before deciding', async () => {
    const fetchMock = mockFetch({});
    renderWithProviders(<StaffReviewPanel claim={claim('IN_REVIEW', ['REQUEST_INFO', 'DECIDE'])}
      onRefresh={() => undefined} />);
    await userEvent.click(screen.getByTestId('action-request-info'));
    expect(screen.getByText(/Explain what you need/)).toBeInTheDocument();
    await userEvent.click(screen.getByTestId('decision-rejected'));
    await userEvent.click(screen.getByTestId('action-decide'));
    expect(screen.getByText(/A rejection reason is required/)).toBeInTheDocument();
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('confirms a decision and sends the version the user saw', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const fetchMock = mockFetch({
      'POST /staff/claims/c1/decision': () => jsonResponse(claim('APPROVED', ['CLOSE'])),
    });
    renderWithProviders(<StaffReviewPanel claim={claim('IN_REVIEW', ['DECIDE'])} onRefresh={() => undefined} />);
    await userEvent.click(screen.getByTestId('decision-approved'));
    await userEvent.type(screen.getByTestId('decision-reason'), 'Photos and invoice reviewed.');
    await userEvent.click(screen.getByTestId('action-decide'));
    expect(screen.getByRole('dialog')).toHaveTextContent('Approve this claim?');
    await userEvent.click(screen.getByTestId('confirm-action'));
    await waitFor(() => expect(screen.getByText('Claim approved.')).toBeInTheDocument());
    const call = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/decision'))!;
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual(
      { version: 4, decision: 'APPROVED', reason: 'Photos and invoice reviewed.' });
  });

  it('shows a conflict with a refresh option instead of overwriting', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockFetch({
      'POST /staff/claims/c1/assign': () => jsonResponse({ code: 'STALE_CLAIM',
        message: 'This claim was changed by someone else since you opened it. Refresh to see the latest version.' }, 409),
    });
    const onRefresh = vi.fn();
    renderWithProviders(<StaffReviewPanel claim={claim('SUBMITTED', ['ASSIGN_TO_ME'])} onRefresh={onRefresh} />);
    await userEvent.click(screen.getByTestId('action-assign'));
    expect(await screen.findByTestId('action-error')).toHaveTextContent('This claim changed');
    await userEvent.click(screen.getByTestId('refresh-claim'));
    expect(onRefresh).toHaveBeenCalled();
  });
});

describe('OwnerResponseCard', () => {
  it('shows the adjuster request and is hidden when no response is possible', () => {
    const { unmount } = renderWithProviders(<OwnerResponseCard onRefresh={() => undefined}
      claim={claim('NEEDS_INFO', ['RESPOND'], { openRequest: { message: 'Upload the receipt please.',
        requestedBy: 'Dana Okafor', requestedAt: '2026-03-02T10:00:00Z' } })} />);
    expect(screen.getByTestId('open-request')).toHaveTextContent('Upload the receipt please.');
    unmount();
    renderWithProviders(<OwnerResponseCard onRefresh={() => undefined} claim={claim('IN_REVIEW', [])} />);
    expect(screen.queryByTestId('response-card')).not.toBeInTheDocument();
  });
});
