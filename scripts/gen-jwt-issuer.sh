#!/usr/bin/env bash
# Dev token issuer for the participants' ledger APIs: an RSA key (stays on
# this machine, never mounted) and a self-signed certificate (mounted into the
# participants, which verify RS256 tokens against it). Production uses an IdP.
set -euo pipefail
umask 077
export PATH="/usr/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")/.."
mkdir -p canton/jwt
if [ ! -f canton/jwt/issuer.key ]; then
  openssl genrsa -out canton/jwt/issuer.key 2048 2>/dev/null
  openssl req -new -x509 -sha256 -days 825 -key canton/jwt/issuer.key -out canton/jwt/issuer.crt \
    -subj "/O=loansync dev/CN=loansync dev token issuer"
  chmod 644 canton/jwt/issuer.crt
  echo "created dev token issuer"
else
  echo "token issuer already exists"
fi
