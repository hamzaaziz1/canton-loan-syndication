#!/usr/bin/env bash
# Privacy proof. After a network demo run, search every node's database for
# the run's contract ids, as the postgres superuser, bypassing Canton, and
# compare against the stakeholder matrix. Needs: make up, then make demo.
set -euo pipefail
cd "$(dirname "$0")/.."

RUN=out/last-run.json
[ -f "$RUN" ] || { echo "no $RUN, run make demo first"; exit 1; }
PSQL=(docker compose -f infra/docker-compose.yml exec -T postgres psql -U postgres -qtA)
DBS=(participant_agent participant_lendera participant_lenderb sequencer mediator)
FAILED=0
DETAILS=""

# where <db> <hex>: tables and columns containing the needle, or "-"
where() {
  { cat scripts/sql/find-needle.sql
    echo "select coalesce(string_agg(tbl || '.' || col, ', '), '-') from pg_temp.find_needle(decode(:'hex', 'hex'), :'hex');"
  } | "${PSQL[@]}" -d "$1" -v hex="$2"
}

# row <label> <hex> <expected>: expected is one char per db, y, n or ? (report only)
row() {
  local label=$1 hex=$2 expect=$3 i=0
  printf '%-24s' "$label"
  for db in "${DBS[@]}"; do
    local found want got cell
    found=$(where "$db" "$hex")
    want=${expect:$i:1}; i=$((i + 1))
    if [ "$found" = "-" ]; then got=n; cell="  -"; else got=y; cell="  HAS"; DETAILS+="$label @ $db: $found"$'\n'; fi
    if [ "$want" != "?" ] && [ "$got" != "$want" ]; then cell="$cell !!"; FAILED=1; fi
    printf '%-12s' "$cell"
  done
  echo
}

read -r FAC PART_A PART_B LENDER_A_HEX < <(python3 -c '
import json
d = json.load(open("out/last-run.json"))
print(d["facilityCid"], d["participations"][0], d["participations"][1],
      d["parties"]["lenderA"].encode().hex())')

printf '%-24s' ""
printf '%-12s' agent lendera lenderb sequencer mediator
echo
row "facility"               "$FAC"         "ynnnn"
row "lenderA participation"  "$PART_A"      "yynnn"
row "lenderB participation"  "$PART_B"      "ynynn"
row "LenderA party id"       "$LENDER_A_HEX" "?????"

echo
echo "where found:"
printf '%s' "$DETAILS"
echo
if [ "$FAILED" -ne 0 ]; then
  echo "PRIVACY PROOF FAILED: a cell marked !! differs from the stakeholder matrix"
  exit 1
fi
echo "privacy proof passed: every contract is stored only on its stakeholders' nodes"
