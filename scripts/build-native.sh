#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/../../work/native-build"
mkdir -p "$BUILD" "$ROOT/android/app/src/main/jniLibs/arm64-v8a" "$ROOT/dist/Pebble Notes.app/Contents/MacOS"
python3 "$ROOT/scripts/check-releases.py"
# Remove generated artefacts from the former executable/resource names.
rm -f "$ROOT/dist/Pebble Notes.app/Contents/MacOS/LeafNotes" "$ROOT/dist/Pebble Notes.app/Contents/Resources/Leaf.icns" "$ROOT/android/app/src/main/jniLibs/arm64-v8a/libleaf.so"
clang -O2 -I"$ROOT/core/vendor" -I"$ROOT/core" -c "$ROOT/core/pebble.c" -o "$BUILD/pebble.o"
clang -O2 -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION -c "$ROOT/core/vendor/sqlite3.c" -o "$BUILD/sqlite.o"
swiftc -module-cache-path "$BUILD/sdk-cache" -O -parse-as-library -import-objc-header "$ROOT/core/pebble.h" "$ROOT"/mac/Sources/*.swift "$BUILD/pebble.o" "$BUILD/sqlite.o" -o "$ROOT/dist/Pebble Notes.app/Contents/MacOS/PebbleNotes"
cp "$ROOT/mac/Info.plist" "$ROOT/dist/Pebble Notes.app/Contents/Info.plist"
mkdir -p "$ROOT/dist/Pebble Notes.app/Contents/Resources"
cp -R "$ROOT/mac/Resources/"* "$ROOT/dist/Pebble Notes.app/Contents/Resources/"
codesign --force --sign - "$ROOT/dist/Pebble Notes.app"
NDK="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/27.0.12077973}"
"$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android29-clang" -O2 -fPIC -shared -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION -I"$ROOT/core" -I"$ROOT/core/vendor" "$ROOT/core/pebble.c" "$ROOT/core/vendor/sqlite3.c" "$ROOT/android/app/src/main/cpp/bridge.c" -ldl -lm -o "$ROOT/android/app/src/main/jniLibs/arm64-v8a/libpebble.so"
