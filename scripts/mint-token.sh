#!/usr/bin/env bash
# Mints a ledger API access token for one participant user, in the scope-based
# format Canton expects by default, signed RS256 by the dev token issuer.
# Usage: scripts/mint-token.sh <user-id> [ttl-seconds]
set -euo pipefail
export PATH="/usr/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")/.."
USER_ID=${1:?usage: mint-token.sh <user-id> [ttl-seconds]}
TTL=${2:-86400}
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"%s","scope":"daml_ledger_api","iat":%d,"exp":%d}' "$USER_ID" "$now" "$((now + TTL))" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -sign canton/jwt/issuer.key -binary | b64url)
printf '%s.%s.%s\n' "$header" "$payload" "$signature"
