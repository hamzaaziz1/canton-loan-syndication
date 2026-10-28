#!/usr/bin/env bash
# Runs the m1 syndicate flow across the three participant nodes.
# Needs the stack up (make up) and the test dar built (make build).
set -euo pipefail
cd "$(dirname "$0")/.."

DAR=daml/loan-tests/.daml/dist/loan-tests-0.1.0.dar
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

# 1. participant endpoints on the host, no party routing yet
cat > "$OUT/nodes.json" <<'JSON'
{
  "default_participant": {"host": "localhost", "port": 5011},
  "participants": {
    "agent":   {"host": "localhost", "port": 5011},
    "lendera": {"host": "localhost", "port": 5021},
    "lenderb": {"host": "localhost", "port": 5031}
  },
  "party_participants": {}
}
JSON
echo '["agent", "lendera", "lenderb"]' > "$OUT/node-names.json"

dpm script --dar "$DAR" --script-name Demo.Network:discover \
  --participant-config "$OUT/nodes.json" \
  --input-file "$OUT/node-names.json" --output-file "$OUT/hosted.json"

# 2. routing table and demo input from the discovered parties
python3 - "$OUT" <<'PY'
import json, sys
out = sys.argv[1]
hosted = json.load(open(f"{out}/hosted.json"))
cfg = json.load(open(f"{out}/nodes.json"))
by_hint = {}
for entry in hosted:
    node, party = entry["_1"], entry["_2"]
    cfg["party_participants"][party] = node
    by_hint[party.split("::")[0]] = party
json.dump(cfg, open(f"{out}/routing.json", "w"), indent=2)
json.dump({
    "agent":    by_hint["AgentBank"],
    "borrower": by_hint["Borrower"],
    "lenderA":  by_hint["LenderA"],
    "lenderB":  by_hint["LenderB"],
}, open(f"{out}/parties.json", "w"), indent=2)
for party, node in cfg["party_participants"].items():
    print(f"  {party[:24]}...  ->  {node}")
PY

# 3. the flow itself, each party submitting through its own node
mkdir -p out
cp "$OUT/routing.json" "$OUT/parties.json" out/
dpm script --dar "$DAR" --script-name Demo.Network:closeBookOnNetwork \
  --participant-config "$OUT/routing.json" --input-file "$OUT/parties.json" \
  --output-file out/last-run.json

echo "network demo passed"
