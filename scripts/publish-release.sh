#!/usr/bin/env bash
# One-shot release helper for Vera.
#   - generates a signing key the first time (kept LOCAL — back it up!)
#   - stores GitHub Actions secrets so CI can sign
#   - optionally makes the repo public
#   - tags a version, which triggers the signed-release workflow
#
# Usage:  bash scripts/publish-release.sh [v0.1.0]
# Requires: gh (logged in), keytool (JDK), openssl.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

VERSION="${1:-v0.1.0}"
REPO="swaggpi/Vera"
KS="vera-upload.keystore"
PROPS="keystore.properties"

b64() { base64 -w0 "$1" 2>/dev/null || base64 "$1" | tr -d '\n'; }

# 1) Signing key (generated once). The password is random and saved only in keystore.properties.
if [ ! -f "$KS" ]; then
  echo ">> Generating signing key $KS …"
  PASS="$(openssl rand -base64 24)"
  keytool -genkeypair -v -keystore "$KS" -alias vera -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" -dname "CN=Vera, O=swaggpi, C=DE"
  cat > "$PROPS" <<EOF
storeFile=$(pwd)/$KS
storePassword=$PASS
keyAlias=vera
keyPassword=$PASS
EOF
  echo ">> Created $KS and $PROPS."
  echo ">> ⚠️  BACK UP BOTH FILES SOMEWHERE SAFE — you need them for every future update."
else
  echo ">> Using existing $KS / $PROPS"
fi

STOREPASS="$(grep '^storePassword=' "$PROPS" | cut -d= -f2-)"

# 2) GitHub Actions secrets (so CI can sign the release).
echo ">> Setting GitHub secrets on $REPO …"
b64 "$KS"            | gh secret set KEYSTORE_BASE64   -R "$REPO"
printf '%s' "$STOREPASS" | gh secret set KEYSTORE_PASSWORD -R "$REPO"
printf '%s' "vera"       | gh secret set KEY_ALIAS         -R "$REPO"
printf '%s' "$STOREPASS" | gh secret set KEY_PASSWORD      -R "$REPO"

# 3) Make the repo public (required for open-source distribution).
read -rp ">> Make $REPO PUBLIC now? Your commit email becomes visible. [y/N] " yn
if [ "${yn:-N}" = "y" ] || [ "${yn:-N}" = "Y" ]; then
  gh repo edit "$REPO" --visibility public --accept-visibility-change-consequences
  echo ">> Repo is now public."
fi

# 4) Tag + push -> triggers .github/workflows/release.yml (build + sign + GitHub Release).
echo ">> Tagging $VERSION …"
git tag "$VERSION"
git push origin "$VERSION"

echo ""
echo ">> Done. Watch the build: https://github.com/$REPO/actions"
echo ">> When it's green, the signed APK is at: https://github.com/$REPO/releases/tag/$VERSION"
echo ">> Final step (only you can): submit the IzzyOnDroid request — see docs/DISTRIBUTION.md."
