-- Read model for one organisation's instance (see ADR 0003).
-- Each row is one contract version as received by this organisation's node.
-- A Daml "update" archives a contract and creates its successor, so the
-- current state is the set of rows with no archived_offset.
CREATE TABLE contract (
    contract_id     text        PRIMARY KEY,
    template        text        NOT NULL,   -- Module:Entity, e.g. Syndication.Facility:FacilityAgreement
    payload         jsonb       NOT NULL,   -- the contract's create arguments
    signatories     text[]      NOT NULL,
    observers       text[]      NOT NULL,
    created_offset  bigint      NOT NULL,
    archived_offset bigint,
    projected_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX contract_active_by_template ON contract (template) WHERE archived_offset IS NULL;

-- The last ledger offset whose updates are fully written. Exactly one row.
CREATE TABLE checkpoint (
    id            smallint    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ledger_offset bigint      NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
