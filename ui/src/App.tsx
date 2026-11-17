import { OrgPane } from './OrgPane'

export default function App() {
  return (
    <main>
      <header className="top">
        <h1>Loan syndication on Canton</h1>
        <p>Three organisations, each with its own participant node and its own backend. Each pane shows only what its own node received.</p>
      </header>
      <div className="panes">
        <OrgPane org="agent" title="AgentBank" subtitle="Agent node, also hosting the borrower" users={['agent.ops', 'borrower.treasury']} />
        <OrgPane org="lendera" title="LenderA" subtitle="Lender A's node" users={['lendera.ops']} />
        <OrgPane org="lenderb" title="LenderB" subtitle="Lender B's node" users={['lenderb.ops']} />
      </div>
    </main>
  )
}
