# Releasing Pebble Notes

The public repository is `jeetbania/pebble-notes`. Releases host the Mac ZIP, Android APK, signed `updates.json`, and `SHA256SUMS.txt`. The installed apps read `releases/latest/download/updates.json`; changelogs include intervening versions. The feed and packages must be uploaded together, initially as a draft. Publish only after verifying all assets.

For each release:

1. Increase Mac `CFBundleVersion`, Android `versionCode`, and both version names. Keep the existing bundle/application identifiers and storage paths.
2. Prepend a readable entry to `releases.json`.
3. Run `scripts/build-native.sh`, then `scripts/build-android.sh`. Run the core, Mac, Android, and updater verification checks.
4. Run `python3 scripts/package-release.py` with the locally stored update key.
5. Create a GitHub release tagged `vVERSION`, upload the four files from `dist`, and publish it after checking their hashes and signatures.

Never commit `private/`, build outputs, personal notes, OAuth credentials, or signing keys. Back up both the existing Android keystore and `private/update-signing.pem` securely outside this device. Losing either key would interrupt compatible trusted updates. Signing stays local; no repository secret or hosted build runner has the keys.

Android downloads into its private update cache, verifies the signed manifest, exact size/hash, package ID, version/build and installed signing identity, then opens the system installer. The first time, Android may ask to allow installation from Pebble Notes. A failed download never reaches installation. Periodic checks run approximately every six hours while online; notifications require permission and phone battery policies can delay checks. Manual checking always remains available.

Mac checks while it is running. It verifies the signed manifest and ZIP before extraction, verifies the app bundle and signature, stages the replacement alongside the installed app, waits for the running app to exit, replaces it, and reopens it. Installation requires a writable app location; it never requests administrator privileges. Local signing remains free and is not Apple notarization. Downloaded Mac builds may require normal macOS approval on another Mac.

Both apps retain the original `dev.leafnotes` identities and data directories intentionally. The product name is Pebble Notes; this does not migrate or reset personal data.
