#!/usr/bin/env bash
# Verify every current signer reported by apksigner, never the certificate subject or source stamp.
verify_signing_certs() {
  local expected actual digest count=0
  expected=$(printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || return 1
  actual=$(sed -nE 's/^[[:space:]]*(Signer #[0-9]+|V[23] Signer( #[0-9]+)?):? certificate SHA-256 digest:[[:space:]]*(.*)$/\3/p')
  while IFS= read -r digest; do
    digest=$(printf '%s' "$digest" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
    [[ "$digest" =~ ^[0-9a-f]{64}$ && "$digest" == "$expected" ]] || return 1
    count=$((count + 1))
  done <<< "$actual"
  (( count > 0 ))
}
