import { useEffect, useState } from 'react'
import { actions, ApiError, loadSnapshot, whoAmI, type Credentials, type Org, type Submitted } from './api'
import { usePoll } from './usePoll'

interface Props { org: Org; title: string; subtitle: string; users: string[] }

// Older demo runs all used FAC-001; only facilities with a unique id can be acted on.
const isUnique = (facilityId: string) => facilityId.startsWith('FAC-2')

const money = (amount: number, currency = 'GBP') =>
  new Intl.NumberFormat('en-GB', { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 2 }).format(amount)

function shortId(facilityId: string) {
  const m = facilityId.match(/^FAC-\d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2}:\d{2})/)
  return m ? `FAC ${m[2]}/${m[1]} ${m[3]}` : facilityId
}

const message = (e: unknown) => (e instanceof Error ? e.message : String(e))

function Head({ title, subtitle, offset }: { title: string; subtitle: string; offset?: number | null }) {
  return (
    <div className="pane-head">
      <div>
        <h2>{title}</h2>
        <div className="sub">{subtitle}</div>
      </div>
      <div className="offset">{offset != null ? `node offset ${offset}` : ''}</div>
    </div>
  )
}

export function OrgPane({ org, title, subtitle, users }: Props) {
  // Held in memory only: reloading the page signs everyone out.
  const [creds, setCreds] = useState<Credentials>()
  if (!creds) {
    return (
      <section className="pane">
        <Head title={title} subtitle={subtitle} />
        <SignIn org={org} users={users} onSignedIn={setCreds} />
      </section>
    )
  }
  return <SignedIn org={org} title={title} subtitle={subtitle} creds={creds} onSignOut={() => setCreds(undefined)} />
}

function SignIn({ org, users, onSignedIn }: { org: Org; users: string[]; onSignedIn: (c: Credentials) => void }) {
  const [username, setUsername] = useState(users[0])
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function submit() {
    setBusy(true)
    setError(undefined)
    const creds = { username, password }
    try {
      await whoAmI(org, creds)
      onSignedIn(creds)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Wrong user or password' : message(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="login" onSubmit={e => { e.preventDefault(); void submit() }}>
      <label>
        <span>User</span>
        <select value={username} onChange={e => setUsername(e.target.value)}>
          {users.map(u => <option key={u} value={u}>{u}</option>)}
        </select>
      </label>
      <label>
        <span>Password</span>
        <input type="password" autoComplete="current-password" value={password}
               onChange={e => setPassword(e.target.value)} />
      </label>
      <button disabled={busy || password === ''}>Sign in</button>
      {error && <p className="notice error">{error}</p>}
    </form>
  )
}

function SignedIn({ org, title, subtitle, creds, onSignOut }:
                  { org: Org; title: string; subtitle: string; creds: Credentials; onSignOut: () => void }) {
  const { data, error, refresh } = usePoll(() => loadSnapshot(org, creds))
  const [notice, setNotice] = useState<{ kind: 'ok' | 'error'; text: string }>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) onSignOut()
  }, [error, onSignOut])

  async function run(label: string, action: () => Promise<Submitted>) {
    setBusy(true)
    try {
      const result = await action()
      setNotice({ kind: 'ok', text: `${label}: committed at offset ${result.offset}` })
      await refresh()
    } catch (e) {
      setNotice({ kind: 'error', text: `${label}: ${message(e)}` })
    } finally {
      setBusy(false)
    }
  }

  if (!data) {
    return (
      <section className="pane">
        <Head title={title} subtitle={subtitle} />
        <p className={error ? 'notice error' : 'muted'}>{error ? message(error) : 'Loading…'}</p>
      </section>
    )
  }

  const actingAs = data.status.actingAs
  const facilities = data.facilities.filter(f => isUnique(f.facilityId)).slice(0, 3)
  const participations = data.participations.filter(p => isUnique(p.facilityId)).slice(0, 4)
  const calls = data.fundingCalls.filter(c => isUnique(c.facilityId))
  const requests = data.drawdownRequests.filter(r => isUnique(r.facilityId))

  return (
    <section className="pane">
      <Head title={title} subtitle={subtitle} offset={data.status.ledgerOffset} />
      <div className="signed-in">
        <span>Signed in as <strong>{data.status.user}</strong>, acting as <span className="chip">{actingAs}</span></span>
        <button className="secondary" onClick={onSignOut}>Sign out</button>
      </div>

      <h3>Facilities</h3>
      {data.facilities.length === 0 ? (
        <p className="privacy">No facility contracts visible to {actingAs}. The facility is between the borrower and the agent, so it never reaches a lender's participant.</p>
      ) : (
        <table>
          <thead><tr><th>Facility</th><th className="num">Amount</th><th className="num">Drawn</th><th className="num">Available</th></tr></thead>
          <tbody>
            {facilities.map(f => (
              <tr key={f.contractId}>
                <td title={f.facilityId}>
                  {shortId(f.facilityId)}
                  {actingAs === 'Borrower' && <DrawdownForm disabled={busy} onSubmit={amount =>
                    run('Drawdown request', () => actions.requestDrawdown(creds, f.facilityId, amount))} />}
                </td>
                <td className="num">{money(f.facilityAmount, f.currency)}</td>
                <td className="num">{money(f.drawn, f.currency)}</td>
                <td className="num">{money(f.facilityAmount - f.drawn, f.currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {org === 'agent' && (
        <>
          <h3>Drawdown requests</h3>
          {requests.length === 0 ? <p className="empty">None pending</p> : (
            <table><tbody>
              {requests.map(r => (
                <tr key={r.contractId}>
                  <td title={r.facilityId}>{shortId(r.facilityId)}</td>
                  <td className="num">{money(r.amount)}</td>
                  <td className="num">{actingAs === 'AgentBank'
                    ? <button disabled={busy} onClick={() =>
                        run('Approval with funding calls', () => actions.approveDrawdown(creds, r.contractId))}>Approve</button>
                    : <span className="muted">awaiting agent</span>}</td>
                </tr>
              ))}
            </tbody></table>
          )}
        </>
      )}

      <h3>Funding calls</h3>
      {calls.length === 0 ? <p className="empty">None open</p> : (
        <table><tbody>
          {calls.map(c => (
            <tr key={c.contractId}>
              <td title={c.drawdownRef}>{shortId(c.facilityId)}</td>
              <td>{c.lender}</td>
              <td className="num">{money(c.amount, c.currency)}</td>
              {c.lender === actingAs && <td className="num"><button disabled={busy} onClick={() =>
                run('Funding confirmed', () => actions.confirmFunded(org, creds, c.contractId))}>Confirm</button></td>}
            </tr>
          ))}
        </tbody></table>
      )}

      <h3>Participations</h3>
      {data.participations.length === 0 ? (
        <p className="privacy">No participations visible to {actingAs}. Each participation is between the agent and one lender, so the borrower is not a stakeholder.</p>
      ) : (
        <table><tbody>
          {participations.map(p => (
            <tr key={p.contractId}>
              <td title={p.facilityId}>{shortId(p.facilityId)}</td>
              <td>{p.lender}</td>
              <td className="num">{money(p.amount, p.currency)}</td>
              <td className="num">{p.sharePercent}%</td>
            </tr>
          ))}
        </tbody></table>
      )}

      {notice && <p className={`notice ${notice.kind}`}>{notice.text}</p>}
    </section>
  )
}

function DrawdownForm({ disabled, onSubmit }: { disabled: boolean; onSubmit: (amount: number) => void }) {
  const [millions, setMillions] = useState('10')
  return (
    <form className="inline" onSubmit={e => { e.preventDefault(); onSubmit(Number(millions) * 1_000_000) }}>
      <input type="number" min="0.01" step="0.01" value={millions} aria-label="Drawdown amount in millions"
             onChange={e => setMillions(e.target.value)} />
      <span className="unit">m</span>
      <button disabled={disabled}>Request drawdown</button>
    </form>
  )
}
