# Privacy proof

Canton's claim is that a participant node only receives the parts of a transaction that its hosted parties are stakeholders or informees of. This document shows that claim holding on this project's network, down to the bytes in each node's database.

## Reproduce

```bash
make up      # synchronizer and three participants
make demo    # runs the syndicate flow across the nodes, writes out/last-run.json
make proof   # searches every node's database for that run's contract ids
```

`make proof` exits non-zero if any cell differs from the [stakeholder matrix](design/stakeholder-matrix.md).

## Result

```
                        agent       lendera     lenderb     sequencer   mediator
facility                  HAS         -           -           -           -
lenderA participation     HAS         HAS         -           -           -
lenderB participation     HAS         -           HAS         -           -
LenderA party id          HAS         HAS         HAS         HAS         HAS

where found:
lenderA participation @ participant_lendera: par_contracts.contract_id, par_contracts.instance,
  par_active_contracts.contract_id, lapi_events_deactivate_contract.exercise_result
LenderA party id @ participant_lenderb: common_topology_transactions.instance
LenderA party id @ sequencer: common_topology_transactions.instance
LenderA party id @ mediator: med_response_aggregations.mediator_confirmation_request,
  common_topology_transactions.instance
```

## How the search works

`scripts/privacy-proof.sh` connects to Postgres as the superuser, not through Canton. For each contract id from the demo run, it searches every `bytea` and text column of every table in each node's database, using `scripts/sql/find-needle.sql`. It does not depend on knowing Canton's schema, so "you checked the wrong table" is not an available objection. The search function lives in `pg_temp` and disappears with the session.

## Three layers of evidence

1. **Ledger API.** `checkCloseBookVisibility` runs during `make demo` and queries each party through its own node. Each lender sees its own participation and not the other's. The borrower sees the facility total and no participations.
2. **Participant storage.** LenderA's participation exists in the agent's and lendera's databases and nowhere in lenderb's. LenderB's node never received it, so there was nothing to filter.
3. **Synchronizer storage.** The sequencer and mediator carried every one of these transactions, yet no contract id appears in either database. Transaction views travel encrypted to the participants that need them.

## What each node learned

| Node | Learned | Did not learn |
|---|---|---|
| agent | every contract in the workflow | |
| lendera | its invitation, commitment and participation, and the result of the `Allocate` that created it | the facility, lenderB's allocation |
| lenderb | the same for lenderB | the facility, lenderA's allocation |
| sequencer | which nodes exchanged messages, when, and how large they were | any contract, or which parties a transaction involved |
| mediator | which parties had to confirm each transaction | any contract |

LenderA's participation also appears at lendera in `lapi_events_deactivate_contract.exercise_result`. That is the return value of `Commitment.Allocate`, which lendera sees because LenderA signed the commitment, so it is an informee of the exercise.

## What is not private

- **Party ids and hosting are public topology state.** LenderA's id is in every database, but at lenderb and the sequencer only in `common_topology_transactions`. Every member of a synchronizer knows that LenderA exists and which node hosts it, in the same way every address on a chain is known while balances are not.
- **The mediator learns who transacts.** LenderA's id appears in `med_response_aggregations.mediator_confirmation_request` because the mediator collects confirmations from each informee's node and must know whose confirmations it is waiting for. It never sees what the transaction does.
- **The sequencer sees traffic metadata:** sender, recipients, timing and size of each envelope. In a syndicate this small, a burst of messages to every lender node could hint that a close happened. Padding or batching would be the mitigation, and this project does not implement either.

## Limitations

- Some Canton columns hold compressed blobs, such as exercise arguments with a compression flag, and a byte search cannot see inside them. This does not weaken the absent cells: a node that held a contract would have it in `par_contracts.contract_id`, which is stored uncompressed.
- All databases share one Postgres instance, separated by database and by user. In production each organisation runs its own. The proof searches each database separately, so co-location does not affect the result.
- The borrower is co-hosted with the agent, so its privacy from the agent's operator is not shown here. The lenders are the physical proof.
- The table shows one run. Earlier runs are in the same databases and follow the same pattern.
