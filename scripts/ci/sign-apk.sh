#!/usr/bin/env bash
set -euo pipefail

# Secrets are injected only into this step, after Gradle exits.
for required in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD RELEASE_VERSION_NAME RUNNER_TEMP ANDROID_HOME; do
  if [[ -z "${!required:-}" ]]; then
    echo "Missing required setting or repository secret: $required" >&2
    exit 1
  fi
done

if [[ ! "$RELEASE_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Invalid release version.' >&2
  exit 1
fi

signing_dir=$(mktemp -d "$RUNNER_TEMP/southjack-signing.XXXXXX")
trap 'rm -rf -- "$signing_dir"' EXIT
umask 077
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$signing_dir/release.p12"
unset ANDROID_KEYSTORE_BASE64

build_tools="$ANDROID_HOME/build-tools/36.0.0"
unsigned_apk=app/build/outputs/apk/release/app-release-unsigned.apk
signed_apk="dist/southjack-$RELEASE_VERSION_NAME.apk"
mkdir -p dist

"$build_tools/zipalign" -f -p 4 "$unsigned_apk" "$signing_dir/aligned.apk"
"$build_tools/apksigner" sign \
  --ks "$signing_dir/release.p12" \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --v4-signing-enabled false \
  --out "$signed_apk" \
  "$signing_dir/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$signed_apk"
"$build_tools/apksigner" verify --verbose --print-certs "$signed_apk" > dist/signing-certificate.txt
echo "Verified signed APK: $signed_apk"
