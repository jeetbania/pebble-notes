# Pebble Notes 0.14.1

Android build 17; Mac build 16. This patch polishes Android; Mac includes the updated release history.

## Changes

Purple home glow behind cards, long falloff and a paler light-mode variant. Transparent system bars with adaptive icon contrast and page-matching backgrounds. Removed redundant top-right Settings/brand labels. Action buttons use capsules or circles.

Library, Settings and note chrome use a blurred background with a bottom fade. Header labels remain sharp. Note content reserves space below navigation.

Slash suggestions have distinct block icons, padded 48 dp rows and highlighted first matches. Boards use low-opacity neutral columns with opaque adaptive cards. Task creation has grouped sections, visible fields, local description formatting, and fixed title/Save regions; the Save capsule clears the system gesture area.

Settings opens focused inner pages from a compact grouped index. Profile includes local name, bio, persisted initial colour and optional sampled/cropped photo. Removed promotional and redundant microcopy.

## Validation

Both native builds succeeded. All 18 Android production instrumentation checks passed on Pixel 9 API 37.1, covering storage, editing/formatting, tasks, backups, updates and release acknowledgement. Existing app identities and signing keys are retained.

Emulator GUI checked library scroll blur, light/dark Settings grouping, Appearance navigation, adaptive status icons, home falloff, dark task-board surfaces and persisted task card, task entry and form scrolling with the on-screen keyboard, visible description fields, task save, slash icons and checklist prefix/Return selection. Physical Vivo testing is deferred at the user's request. This record does not claim testing every Android version or physical device.
