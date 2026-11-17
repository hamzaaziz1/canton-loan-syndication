export type Org = 'agent' | 'lendera' | 'lenderb'

export interface Credentials { username: string; password: string }

export interface Status {
  organisation: string; parties: string[]; ledgerOffset: number | null; user: string; actingAs: string
}
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

// Credentials go with every request (ADR 0005). Production sends an identity
// provider's bearer token in the same header instead.
const authorization = (creds: Credentials) => 'Basic ' + btoa(`${creds.username}:${creds.password}`)

async function call<T>(org: Org, creds: Credentials, path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/${org}/api${path}`, {
    ...init,
    headers: { ...(init.headers as Record<string, string> | undefined), Authorization: authorization(creds) },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? (response.status === 401 ? 'Not signed in' : response.statusText))
  }
  return response.json() as Promise<T>
}

// A fresh key per action: the backend uses it as the ledger command id, and the
// participant deduplicates commands by id, so a double submit cannot act twice.
function post<T>(org: Org, creds: Credentials, path: string, body?: unknown): Promise<T> {
  return call<T>(org, creds, path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export const whoAmI = (org: Org, creds: Credentials) => call<Status>(org, creds, '/status')

export async function loadSnapshot(org: Org, creds: Credentials) {
  const [status, facilities, participations, drawdownRequests, fundingCalls] = await Promise.all([
    call<Status>(org, creds, '/status'),
    call<Facility[]>(org, creds, '/facilities'),
    call<Participation[]>(org, creds, '/participations'),
    call<DrawdownRequest[]>(org, creds, '/drawdown-requests'),
    call<FundingCall[]>(org, creds, '/funding-calls'),
  ])
  return { status, facilities, participations, drawdownRequests, fundingCalls }
}

export const actions = {
  requestDrawdown: (creds: Credentials, facilityId: string, amount: number) =>
    post<Submitted>('agent', creds, `/facilities/${encodeURIComponent(facilityId)}/drawdown-requests`, { amount }),
  approveDrawdown: (creds: Credentials, contractId: string) =>
    post<Submitted>('agent', creds, `/drawdown-requests/${contractId}/approve`),
  confirmFunded: (org: Org, creds: Credentials, contractId: string) =>
    post<Submitted>(org, creds, `/funding-calls/${contractId}/confirm`),
}
