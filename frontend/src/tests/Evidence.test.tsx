import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EvidenceList, formatSize, precheckFile } from '../features/attachments/EvidenceSection';
import { ReportsPage } from '../pages/ReportsPage';
import { jsonResponse, mockFetch, renderWithProviders } from './testUtils';

afterEach(() => vi.unstubAllGlobals());

const attachment = {
  id: 'a1', filename: 'receipt.pdf', contentType: 'application/pdf', sizeBytes: 2048, uploadedBy: 'Priya Shah',
  createdAt: '2026-03-02T10:00:00Z', downloadUrl: '/api/v1/claims/c1/attachments/a1',
};

describe('evidence helpers', () => {
  it('prechecks extension and size', () => {
    expect(precheckFile(new File(['x'], 'notes.txt'))).toMatch(/Only PDF, PNG and JPEG/);
    expect(precheckFile(new File([], 'empty.pdf'))).toMatch(/empty/);
    expect(precheckFile(new File(['x'], 'photo.JPG'))).toBeNull();
    expect(formatSize(2048)).toBe('2 KB');
    expect(formatSize(3 * 1024 * 1024)).toBe('3.0 MB');
  });
});

describe('EvidenceList', () => {
  it('lists files with authenticated download links and hides upload when not allowed', async () => {
    mockFetch({ 'GET /claims/c1/attachments': () => jsonResponse([attachment]) });
    renderWithProviders(<EvidenceList claimId="c1" canUpload={false} />);
    const link = await screen.findByTestId('attachment-link');
    expect(link).toHaveAttribute('href', '/api/v1/claims/c1/attachments/a1');
    expect(screen.queryByTestId('evidence-input')).not.toBeInTheDocument();
  });

  it('shows the server rejection for a file whose contents are not allowed', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    mockFetch({
      'GET /claims/c1/attachments': () => jsonResponse([]),
      'POST /claims/c1/attachments': () => jsonResponse({ code: 'UNSUPPORTED_FILE_TYPE',
        message: 'Only PDF, PNG and JPEG files are accepted, and the file extension must match its contents.' }, 415),
    });
    renderWithProviders(<EvidenceList claimId="c1" canUpload />);
    await screen.findByTestId('no-evidence');
    await userEvent.upload(screen.getByTestId('evidence-input'), new File(['not really an image'], 'fake.png',
      { type: 'image/png' }));
    expect(await screen.findByTestId('upload-error')).toHaveTextContent('extension must match its contents');
  });

  it('blocks disallowed extensions before uploading', async () => {
    const fetchMock = mockFetch({ 'GET /claims/c1/attachments': () => jsonResponse([]) });
    renderWithProviders(<EvidenceList claimId="c1" canUpload />);
    await screen.findByTestId('no-evidence');
    await userEvent.upload(screen.getByTestId('evidence-input'), new File(['MZ'], 'tool.exe'),
      { applyAccept: false });
    expect(await screen.findByTestId('upload-error')).toHaveTextContent('Only PDF, PNG and JPEG');
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });
});

describe('ReportsPage', () => {
  it('renders live counts and an export link carrying the filters', async () => {
    mockFetch({
      'GET /staff/reports/summary': () => jsonResponse({
        rows: [{ status: 'SUBMITTED', count: 3, totalEstimated: 4500 }, { status: 'CLOSED', count: 1, totalEstimated: 900 }],
        totalClaims: 4, totalEstimated: 5400,
      }),
    });
    renderWithProviders(<ReportsPage />);
    expect(await screen.findByTestId('report-total')).toHaveTextContent('4');
    expect(screen.getByTestId('report-row-SUBMITTED')).toHaveTextContent('$4,500.00');
    await userEvent.selectOptions(screen.getByLabelText('Export status'), 'CLOSED');
    expect(screen.getByTestId('export-csv')).toHaveAttribute('href', '/api/v1/staff/reports/claims.csv?status=CLOSED');
  });
});
