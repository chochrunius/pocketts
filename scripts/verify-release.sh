#!/usr/bin/env bash
# Run inside the build image. A certificate's subject name is not a signing identity.
set -euo pipefail
APK="${1:?Usage: verify-release.sh APK VERSION VERSION_CODE}"
VERSION="${2:?Missing version}"
CODE="${3:?Missing versionCode}"
EXPECTED=$(printf '%s' "${ALFREDTS_SIGNING_CERT_SHA256:?Set the trusted release certificate SHA-256}" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$EXPECTED" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid trusted certificate SHA-256' >&2; exit 1; }
APKSIGNER=$(find /opt/android-sdk/build-tools -name apksigner | sort -V | tail -1)
CERTS=$("$APKSIGNER" verify --verbose --print-certs "$APK")
source "$(dirname "${BASH_SOURCE[0]}")/verify-signing-certs.sh"
verify_signing_certs "$EXPECTED" <<< "$CERTS" || { echo 'APK signing certificate does not match the trusted release certificate' >&2; exit 1; }
[[ $(apkanalyzer manifest application-id "$APK") == io.github.chochrunius.alfredts ]]
[[ $(apkanalyzer manifest version-name "$APK") == "$VERSION" ]]
[[ $(apkanalyzer manifest version-code "$APK") == "$CODE" ]]
[[ $(apkanalyzer manifest debuggable "$APK") == false ]]
echo 'Release signature, applicationId, version and non-debuggable manifest verified.'
