#!/usr/bin/env bash
# End-to-end check of the backends against the live network, acting as the
# person who would do each step: the borrower's treasury requests a drawdown,
# the agent's operations team approves it (funding calls go out in the same
# transaction), lender A confirms its call. Then what must be refused, at each
# layer. Needs the agent and lendera instances (scripts/backends.sh up) and an
# undrawn facility from `make demo`.
set -euo pipefail
export PATH="/usr/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")/.."
PW=$(grep '^UI_DEV_PASSWORD=' infra/.env | cut -d= -f2-)
AG=localhost:8081/api
LA=localhost:8082/api
J='Content-Type: application/json'
AGENT="agent.ops:$PW"
BORROWER="borrower.treasury:$PW"
LENDERA="lendera.ops:$PW"

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "  ok  $*"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
get()  { curl -sf -u "$1" "$2"; }
# the read model is eventually consistent: poll until a jq condition holds
await() {
  local what=$1 who=$2 url=$3 cond=$4
  for _ in $(seq 1 30); do
    get "$who" "$url" | jq -e "$cond" > /dev/null 2>&1 && { pass "$what"; return; }
    sleep 1
  done
  fail "$what"
}

await "the borrower sees an undrawn facility with a unique id" "$BORROWER" "$AG/facilities" \
  'any(.[]; (.facilityId | startswith("FAC-2")) and .drawn == 0)'
F=$(get "$BORROWER" "$AG/facilities" | jq -r '[.[] | select((.facilityId | startswith("FAC-2")) and .drawn == 0)][0].facilityId')
FE=$(jq -rn --arg v "$F" '$v|@uri')
echo "facility $F"

curl -sf -u "$BORROWER" -X POST "$AG/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":20000000}' > /dev/null \
  || fail "the borrower's drawdown request was refused"
await "the request reached the agent's read model" "$AGENT" "$AG/drawdown-requests" "any(.[]; .facilityId == \"$F\")"
REQ=$(get "$AGENT" "$AG/drawdown-requests" | jq -r --arg f "$F" '[.[] | select(.facilityId == $f)][0].contractId')

[ "$(code -u "$BORROWER" -X POST "$AG/drawdown-requests/$REQ/approve")" = 403 ] \
  && pass "the borrower cannot approve its own drawdown (403)" || fail "the borrower could approve"
curl -sf -u "$AGENT" -X POST "$AG/drawdown-requests/$REQ/approve" > /dev/null || fail "the agent's approval was refused"
await "the facility shows 20m drawn" "$AGENT" "$AG/facilities" "any(.[]; .facilityId == \"$F\" and .drawn == 20000000)"
await "the agent sees both funding calls, 11m and 9m" "$AGENT" "$AG/funding-calls" \
  "[.[] | select(.facilityId == \"$F\") | .amount] | sort == [9000000, 11000000]"
await "lendera sees exactly one call, its own 11m" "$LENDERA" "$LA/funding-calls" \
  "[.[] | select(.facilityId == \"$F\") | {lender, amount}] == [{\"lender\": \"LenderA\", \"amount\": 11000000}]"

get "$BORROWER" "$AG/participations" | jq -e 'length == 0' > /dev/null \
  && pass "the borrower's user sees no participations, though the agent's node stores them" \
  || fail "the borrower's user can see participations"
get "$BORROWER" "$AG/funding-calls" | jq -e 'length == 0' > /dev/null \
  && pass "the borrower's user sees no funding calls" || fail "the borrower's user can see funding calls"

CALL=$(get "$LENDERA" "$LA/funding-calls" | jq -r --arg f "$F" '[.[] | select(.facilityId == $f)][0].contractId')
curl -sf -u "$LENDERA" -X POST "$LA/funding-calls/$CALL/confirm" > /dev/null || fail "lendera's confirmation was refused"
await "lendera's call is confirmed and archived" "$LENDERA" "$LA/funding-calls" "[.[] | select(.facilityId == \"$F\")] | length == 0"

[ "$(code "$AG/facilities")" = 401 ] && pass "no credentials, no data (401)" || fail "an anonymous read was allowed"
[ "$(code -u "agent.ops:wrong" "$AG/facilities")" = 401 ] && pass "a wrong password is refused (401)" || fail "a wrong password was accepted"
[ "$(code -u "$AGENT" -X POST "$AG/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":1}')" = 403 ] \
  && pass "the agent cannot request drawdowns on the borrower's behalf (403)" || fail "the agent could request a drawdown"
[ "$(code -u "$BORROWER" -X POST "$AG/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":90000000}')" = 409 ] \
  && pass "overdrawing is refused by the ledger (409)" || fail "an overdraw was not refused"
[ "$(code -u "$LENDERA" -X POST "$LA/funding-calls/$CALL/confirm")" = 404 ] \
  && pass "confirming twice is refused (404)" || fail "a second confirmation was not refused"
[ "$(code -u "$LENDERA" -X POST "$LA/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":1}')" = 404 ] \
  && pass "a lender cannot reach the facility: it is not in its read model (404)" || fail "a lender could reach the facility"
echo "backend smoke passed"
