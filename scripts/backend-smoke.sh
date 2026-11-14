#!/usr/bin/env bash
# End-to-end check of the backends against the live network: a drawdown
# request, approval with pro-rata funding calls in one transaction, a lender's
# confirmation, and what must be refused. Needs the agent and lendera instances
# (scripts/backends.sh up) and an undrawn facility from `make demo`.
set -euo pipefail
export PATH="/usr/bin:/usr/local/bin:$PATH"
AG=localhost:8081/api
LA=localhost:8082/api
J='Content-Type: application/json'

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "  ok  $*"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# the read model is eventually consistent: poll until a jq condition holds
await() {
  local what=$1 url=$2 cond=$3
  for _ in $(seq 1 30); do
    curl -sf "$url" | jq -e "$cond" > /dev/null 2>&1 && { pass "$what"; return; }
    sleep 1
  done
  fail "$what"
}

await "an undrawn facility with a unique id" "$AG/facilities" \
  'any(.[]; (.facilityId | startswith("FAC-2")) and .drawn == 0)'
F=$(curl -sf $AG/facilities | jq -r '[.[] | select((.facilityId | startswith("FAC-2")) and .drawn == 0)][0].facilityId')
FE=$(jq -rn --arg v "$F" '$v|@uri')
echo "facility $F"

curl -sf -X POST "$AG/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":20000000}' > /dev/null \
  || fail "the borrower's drawdown request was refused"
await "the drawdown request reached the agent's read model" "$AG/drawdown-requests" "any(.[]; .facilityId == \"$F\")"
REQ=$(curl -sf $AG/drawdown-requests | jq -r --arg f "$F" '[.[] | select(.facilityId == $f)][0].contractId')

curl -sf -X POST "$AG/drawdown-requests/$REQ/approve" > /dev/null || fail "the agent's approval was refused"
await "the facility shows 20m drawn" "$AG/facilities" "any(.[]; .facilityId == \"$F\" and .drawn == 20000000)"
await "the agent sees both funding calls, 11m and 9m" "$AG/funding-calls" \
  "[.[] | select(.facilityId == \"$F\") | .amount] | sort == [9000000, 11000000]"
await "lendera sees exactly one call, its own 11m" "$LA/funding-calls" \
  "[.[] | select(.facilityId == \"$F\") | {lender, amount}] == [{\"lender\": \"LenderA\", \"amount\": 11000000}]"

CALL=$(curl -sf $LA/funding-calls | jq -r --arg f "$F" '[.[] | select(.facilityId == $f)][0].contractId')
curl -sf -X POST "$LA/funding-calls/$CALL/confirm" > /dev/null || fail "lendera's confirmation was refused"
await "lendera's call is confirmed and archived" "$LA/funding-calls" "[.[] | select(.facilityId == \"$F\")] | length == 0"

[ "$(code -X POST "$AG/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":90000000}')" = 409 ] \
  && pass "overdrawing is refused by the ledger (409)" || fail "an overdraw was not refused"
[ "$(code -X POST "$LA/funding-calls/$CALL/confirm")" = 404 ] \
  && pass "confirming twice is refused (404)" || fail "a second confirmation was not refused"
[ "$(code -X POST "$LA/drawdown-requests/$REQ/approve")" = 404 ] \
  && pass "a lender cannot approve drawdowns: the request is not in its read model (404)" \
  || fail "a lender could reach the drawdown request"
[ "$(code -X POST "$LA/facilities/$FE/drawdown-requests" -H "$J" -d '{"amount":1}')" = 404 ] \
  && pass "a lender cannot draw on the facility: it is not in its read model (404)" \
  || fail "a lender could reach the facility"
echo "backend smoke passed"
