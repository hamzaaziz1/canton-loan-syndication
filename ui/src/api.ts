export type Org = 'agent' | 'lendera' | 'lenderb'

export interface Status { organisation: string; parties: string[]; ledgerOffset: number | null }
export interface Facility {
  contractId: string; facilityId: string; borrower: string; currency: string
  facilityAmount: number; drawn: number; maturity: string; createdOffset: number
}
export interface Participation {
  contractId: string; facilityId: string; lender: string; currency: string
  amount: number; sharePercent: number; createdOffset: number
}
export interface DrawdownRequest { contractId: string; facilityId: string; amount: number; createdOffset: number }
export interface FundingCall {
  contractId: string; facilityId: string; drawdownRef: string; lender: string
  currency: string; amount: number; createdOffset: number
}
export interface Submitted { updateId: string; offset: number }

export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function call<T>(org: Org, path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/${org}/api${path}`, init)
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? response.statusText)
  }
  return response.json() as Promise<T>
}

// A fresh key per action: the backend uses it as the ledger command id, and the
// participant deduplicates commands by id, so a double submit cannot act twice.
function post<T>(org: Org, path: string, body?: unknown): Promise<T> {
  return call<T>(org, path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function loadSnapshot(org: Org) {
  const [status, facilities, participations, drawdownRequests, fundingCalls] = await Promise.all([
    call<Status>(org, '/status'),
    call<Facility[]>(org, '/facilities'),
    call<Participation[]>(org, '/participations'),
    call<DrawdownRequest[]>(org, '/drawdown-requests'),
    call<FundingCall[]>(org, '/funding-calls'),
  ])
  return { status, facilities, participations, drawdownRequests, fundingCalls }
}

export const actions = {
  requestDrawdown: (facilityId: string, amount: number) =>
    post<Submitted>('agent', `/facilities/${encodeURIComponent(facilityId)}/drawdown-requests`, { amount }),
  approveDrawdown: (contractId: string) =>
    post<Submitted>('agent', `/drawdown-requests/${contractId}/approve`),
  confirmFunded: (org: Org, contractId: string) =>
    post<Submitted>(org, `/funding-calls/${contractId}/confirm`),
}
