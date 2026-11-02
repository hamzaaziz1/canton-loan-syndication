#!/usr/bin/env bash
# Dev TLS material for the compose network, mirroring canton's own
# config/tls/certs-common.sh: RSA 4096 keys as unencrypted PKCS#8 (netty
# requires PKCS#8), leaf certificates signed by a local dev CA.
# Layout under canton/tls (gitignored):
#   ca/        root-ca.key (never mounted into any container), root-ca.crt
#   <node>/    tls.crt, tls.pem, root-ca.crt, mounted into that node only
#   console/   client certificate for admin API mutual TLS
# Existing material is kept; delete a directory to regenerate it.
set -euo pipefail
umask 077
export PATH="/usr/bin:/usr/local/bin:$PATH"   # system openssl, not conda's
cd "$(dirname "$0")/.."
OUT=canton/tls
mkdir -p "$OUT/ca"

if [ ! -f "$OUT/ca/root-ca.key" ]; then
  openssl genrsa -out "$OUT/ca/root-ca.key" 4096 2>/dev/null
  openssl req -new -x509 -sha256 -days 3650 -key "$OUT/ca/root-ca.key" -out "$OUT/ca/root-ca.crt" \
    -subj "/O=loansync dev/OU=network CA/CN=loansync dev root CA" \
    -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"
  echo "created dev CA"
fi

issue() {   # issue <dir> <common name> [subjectAltName]
  local dir="$OUT/$1" cn=$2 san=${3:-}
  if [ -f "$dir/tls.crt" ]; then echo "kept $1"; return; fi
  mkdir -p "$dir"
  openssl genrsa -out "$dir/tls.key" 4096 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in "$dir/tls.key" -out "$dir/tls.pem"
  rm "$dir/tls.key"
  {
    echo "basicConstraints=CA:FALSE"
    echo "keyUsage=critical,digitalSignature,keyEncipherment"
    echo "extendedKeyUsage=serverAuth,clientAuth"
    echo "authorityKeyIdentifier=keyid,issuer"
    if [ -n "$san" ]; then echo "subjectAltName=$san"; fi
  } > "$dir/ext.cnf"
  openssl req -new -sha256 -key "$dir/tls.pem" -subj "/O=loansync dev/CN=$cn" -out "$dir/tls.csr"
  openssl x509 -req -sha256 -days 825 -in "$dir/tls.csr" -extfile "$dir/ext.cnf" \
    -CA "$OUT/ca/root-ca.crt" -CAkey "$OUT/ca/root-ca.key" -CAcreateserial -out "$dir/tls.crt" 2>/dev/null
  rm "$dir/tls.csr" "$dir/ext.cnf"
  cp "$OUT/ca/root-ca.crt" "$dir/root-ca.crt"
  chmod 644 "$dir/tls.crt" "$dir/root-ca.crt"
  chmod 600 "$dir/tls.pem"
  chmod 755 "$dir"
  sudo chown -R 1001:1001 "$dir"    # canton runs as uid 1001 inside the image
  echo "issued $1"
}

# IP:0.0.0.0 because each node binds its admin API on 0.0.0.0, and its own
# in-process console connects to that address and checks it against this cert
issue synchronizer synchronizer "DNS:synchronizer,DNS:localhost,IP:127.0.0.1,IP:0.0.0.0"
for p in agent lendera lenderb borrower; do
  issue "participant-$p" "participant-$p" "DNS:participant-$p,DNS:localhost,IP:127.0.0.1,IP:0.0.0.0"
done
issue console console-operator
