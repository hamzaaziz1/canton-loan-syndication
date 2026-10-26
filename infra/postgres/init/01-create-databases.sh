#!/usr/bin/env bash
# Runs once, on an empty data volume. One user per operator; each user can
# connect only to its own databases.
set -euo pipefail

create_owner() {
  local user="$1" password="$2"; shift 2
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER "$user" WITH PASSWORD '$password';
SQL
  for db in "$@"; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE DATABASE "$db" OWNER "$user";
REVOKE CONNECT ON DATABASE "$db" FROM PUBLIC;
SQL
  done
}

create_owner synchronizer "$SYNC_DB_PASSWORD"    sequencer mediator
create_owner p_agent      "$AGENT_DB_PASSWORD"   participant_agent
create_owner p_lendera    "$LENDERA_DB_PASSWORD" participant_lendera
create_owner p_lenderb    "$LENDERB_DB_PASSWORD" participant_lenderb
create_owner projector    "$PROJECTOR_DB_PASSWORD" projector
