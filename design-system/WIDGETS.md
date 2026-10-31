# Home-screen widgets

Android 10+ · Native RemoteViews · Pebble 0.23.0

## Intent

A small, useful part of Pebble lives on the home screen. Inspired by Apple Notes' clear heading, paper-like content and quiet row separators, without copying its yellow banner. One widget has one purpose; it opens the corresponding content rather than recreating an editor. No decorative animation, scrolling feed, attachment loading or wallpaper blur.

The [Apple widget guidelines](https://developer.apple.com/design/human-interface-guidelines/widgets) inform glanceable content and size-specific composition. Implementation follows Android's [widget overview](https://developer.android.com/develop/ui/views/appwidgets/overview), [responsive layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts) and [configuration contract](https://developer.android.com/develop/ui/views/appwidgets/configuration).

## Components and sizes

Three gallery entries: Small (2×2), Medium (4×2), Large (4×4). Cell dimensions depend on the launcher. Each entry resizes in both directions; Android 12+ chooses from three RemoteViews layouts. Earlier versions use available dimensions. Compact composition is the fallback when the host cannot fit a wider layout.

| Variation | Content | Interaction |
| --- | --- | --- |
| Recent notes | Latest edited live notes | Open a note |
| Pinned notes | Pinned live notes | Open a note |
| Collection | Notes in a chosen collection, including descendants | Open note or collection; create within collection |
| Single note | Title and plain-text excerpt | Open selected note |
| Checklist | Items from a chosen note; completion count | Open note or toggle a check circle |
| Quote | First Quote block in a selected note; note title as source | Open source note; never invent an author |
| Tasks | Pending tasks, earliest due first; optional list | Open task, complete it, or open task list |
| Quick capture | New note, checklist, task | Start creation in Pebble |

Small shows one list item or a title/quote with no body excerpt. Medium shows up to two items; Large up to four, or three capture actions. Larger system text reduces row count, removes details, and at the largest setting hides the footer. Full content remains accessible by opening Pebble. Avoid compressing targets to fit extra rows.

## Launcher-specific foundations

These values are intentionally local to RemoteViews, which cannot consume Compose's runtime theme/material. They are not replacements for the app's semantic colours.

- Rounded surface: 28 dp; inset 12 dp; divider 1 dp; action targets 44 dp.
- Header: 14 sp bold; row title: 15 sp bold; detail: 12 sp; footer: 11 sp. Feature title 18/19 sp; excerpt 14 sp; quote 18/23 sp by size. Honour system font scaling.
- Glass: warm ivory translucent gradient, `#F7FFF9E8` → `#EDEDE9DF`, white hairline `#D9FFFFFF`. High opacity protects readability over arbitrary wallpapers. This is a glass-like tint, not real wallpaper blur.
- Paper: opaque off-white. Midnight: dark charcoal. Each has a coordinated ink, secondary ink and accent; note custom text colours never leak into launcher widgets.
- Light ink `#24211C`, secondary `#5B5750`, accent `#765B06`; Midnight ink `#FAF7EE`, secondary `#D1CDC3`, accent `#FFDC75`.
- Check circles use one native vector-like drawing at 24 dp, inside a 44 dp target; checked state adds a tick, not colour alone.

## Configuration and recovery

Configure content, source and surface before adding. The preview uses the actual renderer. Save commits settings; Cancel leaves them unchanged. Tap the heading to reconfigure an existing widget. Required note/collection sources must be selected. Empty notes, empty lists and deleted sources provide readable recovery guidance.

Widgets only project live, unarchived notes; folder records, erased records and media paths are excluded. The setup page explains that selected content is visible on the home screen. Widget choices belong to the device and do not modify or sync a note's appearance.

Completion runs through the existing store, revision history and sync pipeline. An immutable note revision token prevents duplicate or stale taps from toggling again or overwriting a newer edit. Conflicted heads are not changed from a widget. Recurring task completion uses the existing recurrence rules. Refresh immediately after actions and shortly after store changes; periodic refresh is requested every 30 minutes and is controlled by Android. No network polling or per-frame work.

## Verification boundary

Native instrumentation covers content filtering, durable completion, stale taps, recurrence, provider registration, all eight variations in three sizes, empty states, three surfaces, and checklist layouts at 200% system type. Rendered previews are inspected at their actual native density. Vivo launcher placement/resizing, TalkBack traversal and physical-device battery behaviour still require on-device verification. macOS WidgetKit is not part of this Android release.
