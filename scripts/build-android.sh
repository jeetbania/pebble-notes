#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/scripts/check-releases.py"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home" ]; then export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"; fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/../../work/gradle-home}"
bash "$ROOT/android/gradlew" -p "$ROOT/android" :app:assembleDebug --no-daemon
mkdir -p "$ROOT/dist"
VERSION=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[0]["version"])' "$ROOT/releases.json")
cp "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/dist/PebbleNotes-$VERSION-development.apk"
