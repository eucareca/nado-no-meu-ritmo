#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
WEB="$ROOT/android/app/src/main/assets/www"
mkdir -p "$WEB"
for name in index.html appearance.js auto-backup.js photo-studio.js app.js health-connect.js training-planner.js calendar-sync.js local-store.js settings.js styles.css manifest.webmanifest service-worker.js icon.svg icon-192.png icon-512.png; do
    cp "$ROOT/$name" "$WEB/$name"
done
