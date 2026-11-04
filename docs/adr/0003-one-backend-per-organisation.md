# ADR 0003: One backend per organisation

Status: accepted, 2026-11-04

## Context

Each organisation in the syndicate runs its own participant node. The backend reads the ledger, keeps a queryable read model in Postgres, and exposes a REST API for the UI.

## Decision

The backend is one codebase, deployed once per organisation. Each instance connects only to its own organisation's participant and acts only for that organisation's parties:

| Instance | Participant | Parties |
|---|---|---|
| agent | participant-agent | AgentBank, Borrower |
| lendera | participant-lendera | LenderA |
| lenderb | participant-lenderb | LenderB |

Each instance writes its read model to its own Postgres schema.

## Why not one shared backend

A single backend reading every node into one database would rebuild exactly what Canton avoids: one place holding every party's data. The [privacy proof](../privacy-proof.md) would still pass at the node level and fail at the application level. With one instance per organisation, an instance's database can only ever contain what its own node received.

## Consequences

- The privacy boundary holds end to end: node, read model, API and UI.
- Running every instance on one machine is a demo convenience. The schemas share one Postgres server and one database user (`projector`). In production each organisation has its own database and credentials.
- PQS (`scribe`, shipped with dpm) is the off-the-shelf alternative to a custom projector. A custom one was chosen so that the update stream, offsets and contract versioning are explicit in the code.
