import { useState } from 'react'
import { actions, loadSnapshot, type Org, type Submitted } from './api'
import { usePoll } from './usePoll'

interface Props { org: Org; title: string; subtitle: string }

// Older demo runs all used FAC-001; only facilities with a unique id can be acted on.
const isUnique = (facilityId: string) => facilityId.startsWith('FAC-2')

const money = (amount: number, currency = 'GBP') =>
  new Intl.NumberFormat('en-GB', { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 2 }).format(amount)

function shortId(facilityId: string) {
  const m = facilityId.match(/^FAC-\d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2}:\d{2})/)
  return m ? `FAC ${m[2]}/${m[1]} ${m[3]}` : facilityId
}

export function OrgPane({ org, title, subtitle }: Props) {
  const { data, error, refresh } = usePoll(() => loadSnapshot(org))
  const [notice, setNotice] = useState<{ kind: 'ok' | 'error'; text: string }>()
  const [busy, setBusy] = useState(false)
  const isAgent = org === 'agent'

  async function run(label: string, action: () => Promise<Submitted>) {
    setBusy(true)
    try {
      const result = await action()
      setNotice({ kind: 'ok', text: `${label}: committed at offset ${result.offset}` })
      await refresh()
    } catch (e) {
      setNotice({ kind: 'error', text: `${label}: ${e instanceof Error ? e.message : String(e)}` })
    } finally {
      setBusy(false)
    }
  }

  const head = (
    <div className="pane-head">
      <div>
        <h2>{title}</h2>
        <div className="sub">{subtitle}</div>
      </div>
      <div className="offset">{data?.status.ledgerOffset != null ? `node offset ${data.status.ledgerOffset}` : ''}</div>
    </div>
  )
  if (!data) {
    return <section className="pane">{head}<p className={error ? 'notice error' : 'muted'}>{error ?? 'connecting…'}</p></section>
  }

  const facilities = data.facilities.filter(f => isUnique(f.facilityId)).slice(0, 3)
  const participations = data.participations.filter(p => isUnique(p.facilityId)).slice(0, 4)
  const calls = data.fundingCalls.filter(c => isUnique(c.facilityId))
  const requests = data.drawdownRequests.filter(r => isUnique(r.facilityId))

  return (
    <section className="pane">
      {head}
      <div className="chips">{data.status.parties.map(p => <span key={p} className="chip">{p}</span>)}</div>

      <h3>Facilities</h3>
      {data.facilities.length === 0 ? (
        <p className="privacy">No facility contracts on this node. The facility is between the borrower and the agent, so it never reaches a lender's participant.</p>
      ) : (
        <table>
          <thead><tr><th>Facility</th><th className="num">Amount</th><th className="num">Drawn</th><th className="num">Available</th></tr></thead>
          <tbody>
            {facilities.map(f => (
              <tr key={f.contractId}>
                <td title={f.facilityId}>
                  {shortId(f.facilityId)}
                  {isAgent && <DrawdownForm disabled={busy} onSubmit={amount =>
                    run('Drawdown request (as Borrower)', () => actions.requestDrawdown(f.facilityId, amount))} />}
                </td>
                <td className="num">{money(f.facilityAmount, f.currency)}</td>
                <td className="num">{money(f.drawn, f.currency)}</td>
                <td className="num">{money(f.facilityAmount - f.drawn, f.currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {isAgent && (
        <>
          <h3>Drawdown requests</h3>
          {requests.length === 0 ? <p className="empty">None pending</p> : (
            <table><tbody>
              {requests.map(r => (
                <tr key={r.contractId}>
                  <td title={r.facilityId}>{shortId(r.facilityId)}</td>
                  <td className="num">{money(r.amount)}</td>
                  <td className="num"><button disabled={busy} onClick={() =>
                    run('Approval with funding calls', () => actions.approveDrawdown(r.contractId))}>Approve</button></td>
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
              {!isAgent && <td className="num"><button disabled={busy} onClick={() =>
                run('Funding confirmed', () => actions.confirmFunded(org, c.contractId))}>Confirm</button></td>}
            </tr>
          ))}
        </tbody></table>
      )}

      <h3>Participations</h3>
      {participations.length === 0 ? <p className="empty">None</p> : (
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
