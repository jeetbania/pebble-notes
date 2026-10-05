# Pebble Notes

A calm, offline-first home for notes, images, checklists, and tasks on Mac and Android. Formerly Leaf Notes. Existing app identities and storage directories are retained so updates keep your library and account connection.

[Download the latest release](https://github.com/jeetbania/pebble-notes/releases/latest)

## What’s inside

- Rich writing with headings, formatting, lists, checklists, tables, nested toggles, and slash commands.
- Original images, captions, mixed-content notes, gallery browsing, zoom, and pan.
- Collections, tags, pinned notes, search, archive, 30-day Trash, revision history, and portable backups.
- Tasks with status, dates, priorities, reminders, repeating dates, subtasks, and board/list views.
- Optional Google Drive sync, local draft recovery, and conflict review.
- A skippable welcome tour, local profile with a picture on Mac, accent choices, and light/dark appearance.
- Signed update information, in-app downloads, package verification, changelogs, and Android update notifications.

## Install and update

Install the Android APK over the previous app; do not uninstall it. Android may ask to allow updates from Pebble Notes before opening its system installer. Open the Mac ZIP and move Pebble Notes to a writable folder. For an existing installation, replace the old app rather than keeping two copies running.

Settings → Updates checks for new releases. Download now or Later lets you choose when to update. Android checks approximately every six hours while online; notification permission and battery policies affect alerts. Mac checks while the app is open. All packages and feed signatures are verified before installation. See [release instructions](docs/RELEASES.md).

These are personal development builds: Mac is locally signed, not Apple-notarized; Android retains the established private signing identity. Mac builds currently use the installed macOS SDK’s deployment defaults and have been tested on the development Mac. Android is ARM64, API 29+.

## Privacy and recovery

Your optional profile stays on that device. Notes save locally first; Google Drive sync is optional. The public release service receives ordinary download/check requests, not your note contents. Notes are not end-to-end encrypted, and locked notes are not implemented. Export a backup to another device to protect against device loss.

Concurrent edits keep both revisions for review. Permanent Trash deletion removes retained local revisions and unreferenced media while preserving a deletion marker against stale synced copies.

## Build

The source combines SwiftUI/AppKit on Mac, Kotlin/Compose on Android, and a shared C/SQLite storage engine. Run `scripts/build-native.sh`, then `scripts/build-android.sh`. Private Android signing credentials and the update signing key are deliberately excluded. See `docs/RELEASES.md` for packaging and publishing.

Checks use production storage and editor adapters: `tests/test_core.py`, `tests/MacSmoke.swift`, and Android instrumentation under `android/app/src/androidTest`. Update checks cover metadata tampering, missed changelogs, app identity/build matching, and constrained installer file access.

## Still being verified

Version 0.14.0 passed 19 core, 26 Mac and 18 Android emulator checks; see [the acceptance record](docs/UPDATE-0.14.md). The desktop interaction pass covers the editor, Profile, tasks and batch selection. The broader phone UI and physical keyboard pass are next.

The physical Vivo previously passed all 17 Android automated checks on 0.13.1; live welcome navigation, keyboard resizing/dismissal and preserved library were checked over USB. Future-version installation, live simultaneous-device conflicts, interrupted image uploads, cloud cleanup after permanent deletion, and sleep/background timing still need acceptance. OCR/scanning, drawings, audio, widgets, encrypted locks, and collaborative editing are later work. Google OAuth long-term configuration is separate from app distribution.

SQLite is public domain; Lucide vectors retain their MIT license in `assets/LUCIDE-LICENSE`. Dependencies retain their upstream licenses.

## Design system

[The Pebble design system](design-system/README.md) documents shared tokens, native components, motion, layouts and interaction rules for Mac and Android. Edit its token manifest and run `python3 scripts/design-tokens.py` to generate the Swift/Kotlin foundation constants; `--check` verifies they are current. Future UI work should reuse these components and rules by default.
