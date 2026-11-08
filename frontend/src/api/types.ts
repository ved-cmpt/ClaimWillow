export type Role = 'POLICYHOLDER' | 'ADJUSTER' | 'ADMIN';

export interface CurrentUser {
  id: string;
  email: string;
  displayName: string;
  role: Role;
}

export type PolicyStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export interface Policy {
  id: string;
  policyNumber: string;
  status: PolicyStatus;
  productName: string;
  insuredAddress: string;
  effectiveDate: string;
  expirationDate: string;
  coverageLimit: number;
  deductible: number;
  currency: 'CAD';
}

export function isStaff(role: Role | undefined): boolean {
  return role === 'ADJUSTER' || role === 'ADMIN';
}
