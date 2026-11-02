#!/usr/bin/env bash
# Offline root key ceremony for the borrower participant.
# Runs at the offline site, OFFLINE_DIR: outside the repo, never mounted into a
# container. Creates the root key once and never overwrites it, signs the root
# certificate, then signs the delegation to the node's intermediate key after a
# human checks the transferred key's hash. Only the .cert files leave this site.
set -euo pipefail
umask 077
export PATH="/usr/bin:/usr/local/bin:$PATH"   # system openssl, not conda's

OFFLINE_DIR="${OFFLINE_DIR:-$HOME/canton-offline-root}"
TOOLS="$OFFLINE_DIR/scripts/offline-root-key"
cd "$OFFLINE_DIR/certs"
[ -f intermediate_key.pub ] || { echo "missing intermediate_key.pub from the node"; exit 1; }

# 1. root key: created once, never overwritten
if [ ! -f root_private_key.der ]; then
  openssl genpkey -algorithm Ed25519 -outform DER -out root_private_key.der
  echo "created a new root key"
else
  echo "using the existing root key"
fi
openssl pkey -inform DER -in root_private_key.der -pubout -outform DER -out root_public_key.der

# 2. root certificate: the root key delegating to itself, the trust anchor
"$TOOLS/prepare-cert.sh" --root-delegation --root-pub-key root_public_key.der \
  --target-pub-key root_public_key.der --output root_namespace > root_namespace.log
openssl pkeyutl -rawin -inkey root_private_key.der -keyform DER -sign \
  -in root_namespace.hash -out root_namespace.signature
"$TOOLS/assemble-cert.sh" --prepared-transaction root_namespace.prep \
  --signature root_namespace.signature --signature-algorithm ed25519 \
  --output root_namespace >> root_namespace.log

# 3. authenticity check before anything is signed for the node
echo
echo "sha256 of the intermediate public key received at the offline site:"
sha256sum intermediate_key.pub | cut -d' ' -f1
read -r -p "does it match INTERMEDIATE_PUB_SHA256 printed by the node? type yes to sign: " ok
[ "$ok" = "yes" ] || { echo "aborted, delegation not signed"; exit 1; }

# 4. delegation: the root authorises the node's intermediate key for this namespace
"$TOOLS/prepare-cert.sh" --intermediate-delegation --root-pub-key root_public_key.der \
  --canton-target-pub-key intermediate_key.pub --output intermediate_namespace | tee intermediate_namespace.log | tail -25
openssl pkeyutl -rawin -inkey root_private_key.der -keyform DER -sign \
  -in intermediate_namespace.hash -out intermediate_namespace.signature
"$TOOLS/assemble-cert.sh" --prepared-transaction intermediate_namespace.prep \
  --signature intermediate_namespace.signature --signature-algorithm ed25519 \
  --output intermediate_namespace >> intermediate_namespace.log

echo
ls -la *.cert
echo "only the .cert files go back to the online site"
