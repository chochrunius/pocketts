#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/verify-signing-certs.sh"
trusted=2C:35:CF:68:DC:45:BA:EC:0C:A2:99:45:6C:AF:7A:F7:A4:5D:FD:FB:1F:DD:6F:18:52:9E:1E:6E:10:DB:BF:D9
other=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
accept() { verify_signing_certs "$trusted" <<< "$1" || { echo "Unexpected rejection: $1" >&2; exit 1; }; }
reject() { if verify_signing_certs "$trusted" <<< "$1"; then echo "Unexpected acceptance: $1" >&2; exit 1; fi; }
accept "Signer #1 certificate SHA-256 digest: ${trusted//:/}"
accept "V2 Signer: certificate SHA-256 digest: $trusted"
accept "V3 Signer: certificate SHA-256 digest: ${trusted,,}"
accept "V3 Signer #1: certificate SHA-256 digest: $trusted"
accept "$(printf 'V2 Signer: certificate SHA-256 digest: %s\nV3 Signer: certificate SHA-256 digest: %s' "$trusted" "$trusted")"
reject "V2 Signer: certificate SHA-256 digest: $other"
reject "$(printf 'Signer #1 certificate SHA-256 digest: %s\nSigner #2 certificate SHA-256 digest: %s' "$trusted" "$other")"
reject 'Signer #1 certificate DN: CN=PocketTS'
reject ''
reject "Source Stamp Signer certificate SHA-256 digest: $trusted"
reject "$(printf 'V2 Signer: certificate SHA-256 digest: %s\nV3 Signer: certificate SHA-256 digest: invalid' "$trusted")"
reject 'V2 Signer: certificate SHA-256 digest: '
if verify_signing_certs invalid <<< "V2 Signer: certificate SHA-256 digest: $trusted"; then exit 1; fi
echo 'Signer certificate parser: 13 regression cases passed.'
