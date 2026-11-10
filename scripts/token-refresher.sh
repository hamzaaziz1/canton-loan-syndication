#!/usr/bin/env bash
# Token sink for the backends, the pattern Vault Agent uses: every 4 minutes,
# mint a fresh 5-minute ledger API token for each backend user into
# canton/jwt/tokens/<user>.jwt. The issuer key stays in this process; each
# backend only reads its own token file. Stop with Ctrl-C.
set -euo pipefail
umask 077
export PATH="/usr/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")/.."
mkdir -p canton/jwt/tokens
USERS=${*:-agent-backend lendera-backend lenderb-backend borrower-backend}
while true; do
  for u in $USERS; do
    ./scripts/mint-token.sh "$u" 300 > "canton/jwt/tokens/$u.jwt.tmp"
    mv "canton/jwt/tokens/$u.jwt.tmp" "canton/jwt/tokens/$u.jwt"
  done
  echo "$(date -u +%H:%M:%S) refreshed tokens for: $USERS"
  sleep 240
done
