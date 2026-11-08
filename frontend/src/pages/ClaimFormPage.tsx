import { useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiRequestError } from '../api/client';
import { useClaim, useSaveDraft, useSubmitClaim } from '../api/claims';
import type { ClaimDetail, DraftInput, IncidentType } from '../api/claims';
import type { Policy } from '../api/types';
import { Alert, Button, Card, CardHeader, PageHeader, SelectField, TextArea, TextField } from '../components/ui';
import { Spinner } from '../components/Spinner';
import { useToast } from '../components/Toast';
import { formatDate, formatMoney, todayIso } from '../lib/format';
import { usePolicies } from './PoliciesPage';
import { EvidenceList } from '../features/attachments/EvidenceSection';

export interface ClaimFormValues {
  policyId: string;
  incidentDate: string;
  incidentType: '' | IncidentType;
  title: string;
  description: string;
  estimatedLoss: string;
}

export type FormErrors = Partial<Record<keyof ClaimFormValues, string>>;

export const MIN_DESCRIPTION = 60;

export const emptyForm: ClaimFormValues = {
  policyId: '', incidentDate: '', incidentType: '', title: '', description: '', estimatedLoss: '',
};

/** Client-side mirror of rule R1 for fast feedback. The server re-validates everything. */
export function validateForSubmit(v: ClaimFormValues, today = todayIso()): FormErrors {
  const e: FormErrors = {};
  if (!v.policyId) e.policyId = 'Select the policy this claim is for.';
  if (!v.incidentDate) e.incidentDate = 'Incident date is required.';
  else if (v.incidentDate > today) e.incidentDate = 'Incident date cannot be in the future.';
  if (!v.incidentType) e.incidentType = 'Choose whether items were damaged or lost.';
  if (!v.title.trim()) e.title = 'A short title is required.';
  if (!v.description.trim()) e.description = 'A description of what happened is required.';
  const amountError = validateAmount(v.estimatedLoss, true);
  if (amountError) e.estimatedLoss = amountError;
  return e;
}

export function validateAmount(raw: string, required: boolean): string | undefined {
  const value = raw.trim();
  if (!value) return required ? 'Estimated loss is required.' : undefined;
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(value)) return 'Enter an amount in dollars, with at most two decimals.';
  if (required && Number(value) < 0.01) return 'Estimated loss must be at least 0.01 CAD.';
  return undefined;
}

function toInput(v: ClaimFormValues, version?: number): DraftInput {
  return {
    policyId: v.policyId || null,
    incidentDate: v.incidentDate || null,
    incidentType: v.incidentType || null,
    title: v.title.trim() || null,
    description: v.description.trim() || null,
    estimatedLoss: v.estimatedLoss.trim() || null,
    version,
  };
}

function fromClaim(c: ClaimDetail): ClaimFormValues {
  return {
    policyId: c.policy?.id ?? '',
    incidentDate: c.incidentDate ?? '',
    incidentType: c.incidentType ?? '',
    title: c.title ?? '',
    description: c.description ?? '',
    estimatedLoss: c.estimatedLoss === null ? '' : c.estimatedLoss.toFixed(2),
  };
}

function serverFieldErrors(err: unknown): FormErrors {
  if (!(err instanceof ApiRequestError)) return {};
  const out: FormErrors = {};
  err.fieldErrors.forEach((f) => {
    if (f.field in emptyForm) out[f.field as keyof ClaimFormValues] = f.message;
  });
  return out;
}

export function ClaimFormPage() {
  const { id } = useParams();
  const existing = useClaim(id);
  const policies = usePolicies();

  if ((id && existing.isPending) || policies.isPending) return <Spinner label="Loading" />;
  if (id && existing.isError) {
    return <Alert tone="error" title="This draft could not be opened.">It may not exist or is no longer a draft.</Alert>;
  }
  if (existing.data && existing.data.status !== 'DRAFT') {
    return (
      <Alert tone="info" title="This claim has already been submitted.">
        <Link className="underline" to={`/claims/${existing.data.id}`}>View the claim</Link>
      </Alert>
    );
  }
  return <ClaimForm key={existing.data?.id ?? 'new'} draft={existing.data} policies={policies.data ?? []}
    policiesLoading={policies.isPending} />;
}

interface ClaimFormProps {
  draft?: ClaimDetail;
  policies: Policy[];
  policiesLoading?: boolean;
}

export function ClaimForm({ draft, policies, policiesLoading = false }: ClaimFormProps) {
  const navigate = useNavigate();
  const toast = useToast();
  const save = useSaveDraft();
  const submit = useSubmitClaim();
  const [values, setValues] = useState<ClaimFormValues>(() => (draft ? fromClaim(draft)
    : { ...emptyForm, policyId: policies.length === 1 ? policies[0].id : '' }));
  const [errors, setErrors] = useState<FormErrors>({});
  const [mode, setMode] = useState<'edit' | 'review'>('edit');
  const [claimId, setClaimId] = useState(draft?.id);
  const [version, setVersion] = useState(draft?.version);
  const [formError, setFormError] = useState<string | null>(null);

  const set = (field: keyof ClaimFormValues) => (value: string) => {
    setValues((v) => ({ ...v, [field]: value }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };

  const selectedPolicy = policies.find((p) => p.id === values.policyId);

  const persist = async (): Promise<ClaimDetail> => {
    const saved = await save.mutateAsync({ id: claimId, input: toInput(values, version) });
    setClaimId(saved.id);
    setVersion(saved.version);
    if (!claimId) navigate(`/claims/${saved.id}/edit`, { replace: true });
    return saved;
  };

  const handleError = (err: unknown) => {
    const fields = serverFieldErrors(err);
    setErrors(fields);
    if (Object.keys(fields).length) setMode('edit');
    setFormError(err instanceof ApiRequestError ? err.message : 'Something went wrong. Please try again.');
  };

  const onSaveDraft = async () => {
    setFormError(null);
    const amountError = validateAmount(values.estimatedLoss, false);
    if (amountError) {
      setErrors((e) => ({ ...e, estimatedLoss: amountError }));
      return;
    }
    try {
      await persist();
      toast.show('Draft saved. You can come back and finish it any time.');
    } catch (err) {
      handleError(err);
    }
  };

  const onReview = (event: FormEvent) => {
    event.preventDefault();
    setFormError(null);
    const found = validateForSubmit(values);
    setErrors(found);
    if (Object.keys(found).length === 0) {
      setMode('review');
      window.scrollTo?.({ top: 0 });
    }
  };

  const onSubmit = async () => {
    setFormError(null);
    try {
      const saved = await persist();
      const submitted = await submit.mutateAsync({ id: saved.id, version: saved.version });
      toast.show(`Claim ${submitted.reference} submitted.`);
      navigate(`/claims/${submitted.id}`, { replace: true });
    } catch (err) {
      handleError(err);
    }
  };

  const busy = save.isPending || submit.isPending;

  return (
    <>
      <PageHeader title={draft ? 'Continue your claim' : 'New claim'}
        subtitle="Fields marked * are required to submit. You can save a draft at any point." />
      {formError && <div className="mb-4"><Alert tone="error" testId="form-error">{formError}</Alert></div>}

      {mode === 'review' ? (
        <ReviewPanel values={values} policy={selectedPolicy} claimId={claimId} busy={busy}
          onBack={() => setMode('edit')} onSubmit={onSubmit} />
      ) : (
        <form noValidate onSubmit={onReview} className="flex flex-col gap-6" data-testid="claim-form">
          <Section number={1} title="Policy">
            <SelectField label="Policy" required value={values.policyId} error={errors.policyId}
              onChange={(e) => set('policyId')(e.target.value)} data-testid="field-policy"
              hint={policiesLoading ? 'Loading your policies…' : 'Choose the policy that covered the belongings.'}>
              <option value="">Select a policy</option>
              {policies.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.policyNumber} · {p.insuredAddress}{p.status !== 'ACTIVE' ? ` (${p.status.toLowerCase()})` : ''}
                </option>
              ))}
            </SelectField>
            {selectedPolicy && (
              <p className="text-sm text-ink-muted" data-testid="selected-policy-terms">
                Period {formatDate(selectedPolicy.effectiveDate)} – {formatDate(selectedPolicy.expirationDate)} ·
                Limit {formatMoney(selectedPolicy.coverageLimit)} · Deductible {formatMoney(selectedPolicy.deductible)}
              </p>
            )}
          </Section>

          <Section number={2} title="Incident and amount">
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Incident date" type="date" required max={todayIso()} value={values.incidentDate}
                onChange={(e) => set('incidentDate')(e.target.value)} error={errors.incidentDate}
                data-testid="field-incident-date" />
              <SelectField label="What happened to the items?" required value={values.incidentType}
                onChange={(e) => set('incidentType')(e.target.value)} error={errors.incidentType}
                data-testid="field-incident-type">
                <option value="">Select</option>
                <option value="DAMAGE">Damaged (water, fire, accident)</option>
                <option value="LOSS">Lost or stolen</option>
              </SelectField>
            </div>
            <TextField label="Short title" required maxLength={120} value={values.title}
              onChange={(e) => set('title')(e.target.value)} error={errors.title}
              placeholder="e.g. Burst pipe damaged living room sofa" data-testid="field-title" />
            <TextArea label="Detailed description" required maxLength={4000} value={values.description}
              onChange={(e) => set('description')(e.target.value)} error={errors.description}
              hint={`What happened, where, and which items were affected. ${values.description.trim().length}/${MIN_DESCRIPTION}+ characters recommended.`}
              data-testid="field-description" />
            <TextField label="Estimated loss (CAD)" required inputMode="decimal" value={values.estimatedLoss}
              onChange={(e) => set('estimatedLoss')(e.target.value)} error={errors.estimatedLoss}
              hint="Your best estimate of the replacement or repair cost, in Canadian dollars."
              placeholder="0.00" className="sm:max-w-xs" data-testid="field-estimate" />
          </Section>

          <Section number={3} title="Evidence">
            {claimId
              ? <EvidenceList claimId={claimId} canUpload />
              : <p className="text-sm text-ink-muted" data-testid="evidence-save-first">Save the draft first, then
                  attach photos, receipts or reports (PDF, PNG or JPEG, up to 5 MB each).</p>}
          </Section>

          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Button variant="secondary" onClick={onSaveDraft} busy={save.isPending} data-testid="save-draft">
              Save draft
            </Button>
            <Button type="submit" disabled={busy} data-testid="review-claim">Review claim</Button>
          </div>
        </form>
      )}
    </>
  );
}

function Section({ number, title, children }: { number: number; title: string; children: ReactNode }) {
  return (
    <Card aria-labelledby={`section-${number}`}>
      <div className="flex items-center gap-3 border-b border-line px-5 py-3">
        <span aria-hidden="true" className="flex h-6 w-6 items-center justify-center rounded-full bg-accent-soft
          text-xs font-semibold text-accent-strong">{number}</span>
        <h2 id={`section-${number}`} className="font-semibold text-ink">{title}</h2>
      </div>
      <div className="flex flex-col gap-4 p-5">{children}</div>
    </Card>
  );
}

interface ReviewPanelProps {
  values: ClaimFormValues;
  policy?: Policy;
  claimId?: string;
  busy: boolean;
  onBack: () => void;
  onSubmit: () => void;
}

function ReviewPanel({ values, policy, claimId, busy, onBack, onSubmit }: ReviewPanelProps) {
  const shortDescription = values.description.trim().length < MIN_DESCRIPTION;
  const rows: [string, string][] = [
    ['Policy', policy ? `${policy.policyNumber} · ${policy.insuredAddress}` : '—'],
    ['Incident date', formatDate(values.incidentDate)],
    ['Type', values.incidentType === 'LOSS' ? 'Lost or stolen' : 'Damaged'],
    ['Title', values.title],
    ['Estimated loss', formatMoney(values.estimatedLoss)],
  ];
  return (
    <Card data-testid="claim-review">
      <CardHeader title="Review before submitting" />
      <div className="flex flex-col gap-5 p-5">
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-[10rem_1fr]">
          {rows.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-sm text-ink-faint">{k}</dt>
              <dd className="text-sm text-ink">{v}</dd>
            </div>
          ))}
          <dt className="text-sm text-ink-faint">Description</dt>
          <dd className="whitespace-pre-wrap text-sm text-ink">{values.description}</dd>
        </dl>
        {claimId && (
          <div>
            <h3 className="mb-2 text-sm font-medium text-ink-faint">Evidence</h3>
            <EvidenceList claimId={claimId} canUpload={false} compact />
          </div>
        )}
        {shortDescription && (
          <Alert tone="info" title="Tip: more detail helps">
            Descriptions under {MIN_DESCRIPTION} characters are flagged for more information. You can still submit.
          </Alert>
        )}
        <p className="text-sm text-ink-muted">
          After you submit, the claim can no longer be edited. Automated checks run immediately and an adjuster
          reviews the claim.
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={onBack} disabled={busy} data-testid="back-to-edit">Back to edit</Button>
          <Button onClick={onSubmit} busy={busy} data-testid="submit-claim">Submit claim</Button>
        </div>
      </div>
    </Card>
  );
}
