// Ledger API users: one per backend instance (ADR 0003), plus a `demo` user on
// each original participant for the network demo scripts. Run BEFORE enabling
// JWT auth on the ledger APIs. Idempotent: existing users are left unchanged.
import com.digitalasset.canton.console.ParticipantReference

def ensure(p: ParticipantReference, id: String, partyNames: Seq[String], admin: Boolean): Unit = {
  if (p.ledger_api.users.list().users.exists(_.id == id)) {
    println(s"${p.name}: user $id already exists")
  } else {
    val parties = partyNames.map(n => p.parties.hosted(filterParty = n + "::").head.party).toSet
    p.ledger_api.users.create(id = id, actAs = parties, participantAdmin = admin)
    println(s"${p.name}: created $id, may act as ${partyNames.mkString(", ")}${if (admin) ", participant admin" else ""}")
  }
}

ensure(agent,    "agent-backend",    Seq("AgentBank", "Borrower"), admin = false)
ensure(lendera,  "lendera-backend",  Seq("LenderA"),               admin = false)
ensure(lenderb,  "lenderb-backend",  Seq("LenderB"),               admin = false)
ensure(borrower, "borrower-backend", Seq("BorrowerCo"),            admin = false)

ensure(agent,   "demo", Seq("AgentBank", "Borrower"), admin = true)
ensure(lendera, "demo", Seq("LenderA"),               admin = true)
ensure(lenderb, "demo", Seq("LenderB"),               admin = true)

participants.remote.sortBy(_.name).foreach { p =>
  println(s"${p.name} users: ${p.ledger_api.users.list().users.map(_.id).sorted.mkString(", ")}")
}
