import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ClaimForm, validateAmount, validateForSubmit, emptyForm } from '../pages/ClaimFormPage';
import type { Policy } from '../api/types';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

const policy: Policy = {
  id: 'p1', policyNumber: 'HC-100421', status: 'ACTIVE', productName: 'Home Contents Plus',
  insuredAddress: '14 Alder Lane', effectiveDate: '2026-01-01', expirationDate: '2026-12-31',
  coverageLimit: 50000, deductible: 500, currency: 'CAD',
};

function claimResponse(overrides: Record<string, unknown> = {}) {
  return {
    id: 'c1', reference: null, title: 'Burst pipe', description: 'd', status: 'DRAFT', incidentType: 'DAMAGE',
    incidentDate: '2026-03-01', estimatedLoss: 1500, currency: 'CAD', policy: { id: 'p1', policyNumber: 'HC-100421' },
    policySnapshot: null, owner: null, assignedAdjuster: null, version: 0, createdAt: '2026-03-02T10:00:00Z',
    submittedAt: null, updatedAt: '2026-03-02T10:00:00Z', decidedAt: null, closedAt: null, lastValidatedAt: null,
    availableActions: ['EDIT_DRAFT', 'SUBMIT'], openRequest: null, ...overrides,
  };
}

function renderForm() {
  return renderWithProviders(
    <Routes>
      <Route path="/claims/new" element={<ClaimForm policies={[policy]} />} />
      <Route path="/claims/:id/edit" element={<ClaimForm policies={[policy]} />} />
      <Route path="/claims/:id" element={<p>Claim detail page</p>} />
    </Routes>,
    { route: '/claims/new' },
  );
}

describe('claim form validation', () => {
  it('marks every missing required field', () => {
    const errors = validateForSubmit(emptyForm, '2026-03-10');
    expect(Object.keys(errors).sort()).toEqual(
      ['description', 'estimatedLoss', 'incidentDate', 'incidentType', 'policyId', 'title'].sort());
  });

  it('rejects future dates and malformed or zero amounts', () => {
    const errors = validateForSubmit({ ...emptyForm, incidentDate: '2026-03-11', estimatedLoss: '0' }, '2026-03-10');
    expect(errors.incidentDate).toBe('Incident date cannot be in the future.');
    expect(errors.estimatedLoss).toBe('Estimated loss must be at least 0.01 CAD.');
    expect(validateAmount('12.345', true)).toMatch(/two decimals/);
    expect(validateAmount('-4', true)).toMatch(/two decimals/);
    expect(validateAmount('', false)).toBeUndefined();
  });
});

describe('ClaimForm', () => {
  it('shows required-field errors and does not call the API when reviewing an empty form', async () => {
    const fetchMock = mockFetch({});
    renderForm();
    await userEvent.click(screen.getByTestId('review-claim'));
    expect(screen.getByText('A short title is required.')).toBeInTheDocument();
    expect(screen.getByText('Incident date is required.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Short title/)).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('saves a draft, reviews and submits the claim', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const fetchMock = mockFetch({
      'POST /claims': () => jsonResponse(claimResponse(), 201),
      'PATCH /claims/c1/draft': () => jsonResponse(claimResponse({ version: 1 })),
      'POST /claims/c1/submit': () => jsonResponse(claimResponse({ status: 'SUBMITTED', reference: 'CW-2026-ABC234' })),
    });
    renderForm();
    await userEvent.type(screen.getByLabelText(/Incident date/), '2026-03-01');
    await userEvent.selectOptions(screen.getByLabelText(/What happened/), 'DAMAGE');
    await userEvent.type(screen.getByLabelText(/Short title/), 'Burst pipe');
    await userEvent.type(screen.getByLabelText(/Detailed description/), 'Water soaked the sofa.');
    await userEvent.type(screen.getByLabelText(/Estimated loss/), '1500');

    await userEvent.click(screen.getByTestId('save-draft'));
    expect(await screen.findByText(/Draft saved/)).toBeInTheDocument();
    const createBody = JSON.parse(fetchMock.mock.calls.find(([u, i]) =>
      String(u).endsWith('/claims') && (i as RequestInit).method === 'POST')![1]!.body as string);
    expect(createBody).toMatchObject({ policyId: 'p1', incidentType: 'DAMAGE', estimatedLoss: '1500', title: 'Burst pipe' });

    await userEvent.click(screen.getByTestId('review-claim'));
    const review = await screen.findByTestId('claim-review');
    expect(review).toHaveTextContent('HC-100421');
    expect(review).toHaveTextContent('$1,500.00');
    expect(review).toHaveTextContent(/more detail helps/);

    await userEvent.click(screen.getByTestId('submit-claim'));
    await waitFor(() => expect(screen.getByText('Claim detail page')).toBeInTheDocument());
    const submitCall = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/claims/c1/submit'))!;
    expect(JSON.parse((submitCall[1] as RequestInit).body as string)).toEqual({ version: 1 });
  });

  it('maps server field errors back onto the form', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockFetch({
      'POST /claims': () => jsonResponse({
        code: 'VALIDATION_FAILED', message: 'Some fields are missing or invalid.',
        fieldErrors: [{ field: 'estimatedLoss', message: 'Estimated loss is too large.' }],
      }, 400),
    });
    renderForm();
    await userEvent.type(screen.getByLabelText(/Estimated loss/), '9999999999');
    await userEvent.click(screen.getByTestId('save-draft'));
    expect(await screen.findByText('Estimated loss is too large.')).toBeInTheDocument();
    expect(screen.getByTestId('form-error')).toHaveTextContent('Some fields are missing or invalid.');
  });
});
