# ADR 0005: People, parties and what each person may see

Status: accepted, 2026-11-16

## Context

[ADR 0004](0004-ledger-api-authorization.md) authorises each backend instance on the ledger API. Inside an instance, anyone who could reach its REST API could still act as any of the instance's parties and read everything in its read model.

The agent's node hosts two parties, AgentBank and Borrower, the way a participant operator hosts parties for its clients. Its read model therefore holds AgentBank's contracts too, including both lenders' participations, which the Borrower party cannot see on the ledger.

## Decision

- People log in to their own organisation's instance, and each person acts as exactly one party:

  | Person | Instance | Acts as |
  |---|---|---|
  | `agent.ops` | agent | AgentBank |
  | `borrower.treasury` | agent | Borrower |
  | `lendera.ops` | lendera | LenderA |
  | `lenderb.ops` | lenderb | LenderB |

- A person may act only as their own party. The backend refuses anything else with 403 before a command reaches the ledger.
- Every read returns only contracts where the person's party is a signatory or an observer (`? = ANY(signatories || observers)`). That is the ledger's own visibility rule applied to the read model, so a person never sees more than their party could, even on a node that stores more.
- Credentials travel in the `Authorization` header of every request. The API is stateless and sets no cookies, so cross-site request forgery has nothing to ride on.

## Three layers of authorisation

| Layer | Question | Enforced by |
|---|---|---|
| Daml model | May this party exercise this choice? | The ledger, in every transaction |
| Ledger API ([ADR 0004](0004-ledger-api-authorization.md)) | May this backend instance act as this party? | The participant, from the token user's rights |
| Backend (this ADR) | May this person act as this party, and see this contract? | Spring Security and the read filter |

`scripts/backend-smoke.sh` checks each layer: the borrower cannot approve its own drawdown and the agent cannot request one for it (403), the borrower's user sees no participations or funding calls although the agent's node stores them, missing or wrong credentials get 401, an overdraw is refused by the Daml model (409), and a lender cannot find the facility at all (404).

## Development setup and production

| Here | Production |
|---|---|
| Users listed in each instance's profile, with one development password from `infra/.env`, sent as HTTP Basic | Each organisation's identity provider (OpenID Connect, for example Keycloak or Entra ID). The UI signs in with the authorization code flow and PKCE, and the backend validates the bearer token as an OAuth resource server, mapping a claim to the party |
| Passwords hashed in memory at startup | No passwords in the backend at all |

## Consequences

- The read filter re-implements the ledger's visibility rule, and the smoke test guards it. A mistake there would not move data to the wrong node, but it would show the right node's data to the wrong person.
- Swapping in an identity provider changes how the `Authorization` header is obtained and validated, not the party mapping or the read filter.
