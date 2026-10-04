# Pebble Notes 0.14.5

Android build 21; Mac build 20. App identities, libraries and signing keys are preserved.

## Changes

- Task cards open TaskComposer directly in List and Board. Tagged tasks use the same editor. Description fields start taller on both platforms.
- Repeat is available before setting a date. Never, daily, weekly, monthly and yearly choices persist in shared storage; choosing recurrence enables scheduling. Completion advances the next occurrence using the platform calendar. Update both devices before using yearly recurrence, because older builds reject that new value.
- Tag pages include matching tasks, which previously were excluded before the tag filter ran.
- Mac task context and overflow menus share actions and include a recoverable Delete task action. Library, collection, photo, block and text-style menus have icons. Actions explicitly use title-and-icon label styling, avoiding the system hiding symbols.
- Completely blank notes move to Trash when the user leaves them or creates another note. A title, tag, task, attachment or non-text block preserves the note. Cleanup never interrupts an active draft and normal Trash recovery remains available.
- The desktop outer frame uses a 22-point curve; the 8-point inset uses a concentric 14-point curve, clipped after the material background.
- About uses three icon rows and one version label. Mac clipboard suggestions fit a single row with preview, Save and Dismiss; phone clipboard copy is shorter.
- Android sheets share compact icon tiles, inset dividers and a subtle border. Status, edit, priority, folder, archive, pin, copy and delete symbols have filled variants. Shared sheet entrances use a restrained spring with synchronized opacity, dimming and blur; normal dismissal retains the short ease-out and calmer-motion setting.

The menu style was checked against Apple documentation: https://developer.apple.com/documentation/swiftui/labelstyle/titleandicon

## Validation

- 19 core tests, 28 Mac smoke checks and 19 Android smoke checks passed. Android checks ran on the final APK in Pixel 9 API 37.1, including blank-note preservation/cleanup and yearly repeat persistence/rollover.
- Both builds succeeded; release notes, Mac signing, package build numbers and all update-feed asset hashes were verified.
- Isolated Mac GUI checks verified task editing, yearly repeat selection and save, tagged-task visibility and editor routing, visible context-menu icons/Delete, concise About with one version, blank-note cleanup, and concentric corners with the sidebar hidden.
- Emulator GUI checks verified whole-card task editing, tag entry, the recurrence dropdown with yearly, date activation and save, update welcome presentation, and filled icons in the final build.
- Physical Vivo verification remains pending. No physical-device or measured frame-timing claim is made. Generic action callbacks outside TaskOptions can still immediately switch their owning surface; coordinated shared entrance and normal dismissal apply globally.
