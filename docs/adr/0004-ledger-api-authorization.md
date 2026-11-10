# ADR 0004: Ledger API authorization

Status: accepted, 2026-11-10

## Context

TLS on the ledger APIs proves which node a client has reached. It says nothing about who the client is: until this change, anyone who could reach a participant's ledger API could act as any party that node hosts.

## Decision

- Every participant's ledger API requires an RS256 JWT, verified against the certificate of a token issuer (`jwt-rs-256-crt`). Participants hold only the certificate.
- Tokens name a ledger API user. Each backend instance has its own user on its own organisation's node, allowed to act as that organisation's parties only, with no admin rights. The demo scripts use a separate `demo` user with admin rights on the nodes they run against.
- Canton's default maximum token lifetime of five minutes is kept. Clients get fresh tokens instead: the demo mints one per run, and each backend reads its token from a file that a token sink renews every four minutes.
- A backend learns which parties it may act as from its own user's rights on its node, not from configuration.

## What the participants enforce

Probed against the agent node with `LedgerSmoke`:

| Token | Ordinary call | Admin call |
|---|---|---|
| none | refused, `UNAUTHENTICATED` | refused, `UNAUTHENTICATED` |
| `demo`, admin | allowed | allowed |
| `agent-backend`, not admin | allowed | refused, `PERMISSION_DENIED` |
| `lendera-backend`, a user that exists only on lendera's node | refused, `PERMISSION_DENIED` | refused, `PERMISSION_DENIED` |
| expired, or valid for longer than five minutes | refused, `UNAUTHENTICATED` | refused, `UNAUTHENTICATED` |

## Development setup and production

| Here | Production |
|---|---|
| An RSA key on the developer's machine, `scripts/mint-token.sh` | An identity provider such as Keycloak, Auth0 or Entra ID, configured with `jwt-jwks`, rotating its own keys |
| `scripts/token-refresher.sh` writes token files | The OAuth client credentials flow inside the backend, or a sidecar agent |
| Users created by `canton/admin/ledger-users.sc` before auth was switched on | Users provisioned by the operator through the user management API |

## Consequences

- A token for one organisation's backend is useless at another organisation's node, because the user does not exist there.
- A stolen token is valid for at most five minutes.
- Console scripts that use the ledger API rather than the admin API, such as `ledger-users.sc`, now need a token as well.
- The backends' REST APIs have no authentication of their own yet and listen on localhost only. Authenticating the UI's users is part of M7.
