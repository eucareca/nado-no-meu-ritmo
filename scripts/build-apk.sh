#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ]; then
    echo 'Defina ANDROID_HOME para o Android SDK.' >&2
    exit 1
fi
"$ROOT/scripts/sync-android-web.sh"
python3 "$ROOT/scripts/audit-source.py"
cd "$ROOT/android"
./gradlew --no-daemon -PnadoPreview=true assembleDebug assembleRelease lintRelease
OUT="$ROOT/artifacts/apk"
mkdir -p "$OUT"
cp app/build/outputs/apk/debug/app-debug.apk "$OUT/nado-teste.apk"
cp app/build/outputs/apk/release/app-release-unsigned.apk "$OUT/nado-release-unsigned.apk"
"$SDK/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$OUT/nado-teste.apk" > "$OUT/preview-signature.txt"
cd "$OUT"
sha256sum nado-teste.apk nado-release-unsigned.apk > SHA256SUMS.txt
printf '%s\n' 'APKs gerados em artifacts/apk/. Leia docs/ATUALIZACAO-ANDROID.md antes de instalar.'
