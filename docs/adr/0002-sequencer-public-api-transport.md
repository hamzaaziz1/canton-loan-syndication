# ADR 0002: Transport security on the sequencer public API

Status: accepted, 2026-11-02

## Context

Admin APIs on every node use mutual TLS, and participant ledger APIs use TLS. The sequencer public API, used by the mediator and all participants, runs plaintext. Canton already encrypts every transaction view to its recipients and signs every message with its own keys, so TLS on this API mainly protects connection metadata and authenticates the sequencer endpoint to its clients.

## What we tried

An in-place migration of the running network, with a database backup and a rollback prepared first.

1. Pointing the clients at TLS while the sequencer still served plaintext. The mediator rejected the change: Canton builds the new connection pool straight away, and it could not connect (`Connection pool failed to initialize within 30 seconds`), even with connection validation disabled.
2. Switching the sequencer to TLS first. The mediator, which runs in the same process and still held a plaintext connection, could not reach the sequencer, and its sequencer connection service was not available until it could (`UNIMPLEMENTED ... SequencerConnectionService/GetConnection`).

Participants could have been migrated in place. Through the console, the co-located mediator could not, from either side.

## Decision

Roll back, and keep the sequencer public API on plaintext for this network. Treat transport security on the public API as a decision made at genesis: a new network starts with TLS on the sequencer and TLS connections for the mediator and participants from its first bootstrap.

## Consequences

- Transaction content stays protected by Canton's own encryption and signatures, as shown in [the privacy proof](../privacy-proof.md). Connection metadata between nodes is visible to anything on the compose network.
- Changing the transport of an existing synchronizer would most likely mean rotating the mediator: onboarding a new mediator with a TLS connection under synchronizer owner governance, then offboarding the old one. That is out of scope here.
- A full backup was taken before the window (`pg_dumpall`, about 118 MB). It contains node private keys and is stored outside the repository.
