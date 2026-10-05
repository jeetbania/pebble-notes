# Pebble Notes 0.14.6

Android build 22; Mac build 21. Application identities, existing libraries and release signing keys are preserved.

## Changes

- Android task descriptions use top/start alignment and retain the taller writing area.
- Toggle disclosure arrows use centered vector icons. Nested text aligns with the parent label; multiline labels align the arrow with the first line.
- Photo zoom controls stay below the toolbar at a fixed top position when zoom changes, rather than moving toward the bottom edge.
- Writing, note-size and photo sliders use pill tracks and round handles. Platform dragging, keyboard and accessibility behavior is retained.
- System Back dismisses active editing, selection, search or library overlays first, then returns Tasks and filtered library sections to Notes before allowing root exit.
- A local Markdown-to-backup converter supports headings, styled text, bullet/number lists, checked and unchecked tasks, tables and contained local images. It never fetches remote images or modifies the source export. See MARKDOWN-IMPORT.md.

## Validation and limits

- 19 shared-core tests, 19 Android instrumentation checks and five Markdown import tests passed.
- Android app/test builds and the signed Mac build succeeded. Release metadata, packaging and signed update-feed hashes were verified.
- Converted Markdown records were validated through the shared core before a real import; existing records were preserved and imported revisions uploaded through the existing sync account.
- Interactive emulator verification could not be completed: the emulator displayed a System UI not responding dialog, and the computer-use service rejected coordinate input with noWindowsAvailable despite exposing screenshots and host accessibility controls. The new layouts and gesture behavior have not been visually verified in this build. Physical-phone verification remains pending.
- Exported missing images retain references; image bytes cannot be restored when absent from the source export. Fenced code and quotes remain literal because Pebble has no dedicated block types for them.
