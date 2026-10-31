# Home-screen widgets

Android 10+ · RemoteViews collection widgets · Pebble 0.24.0

## Visual contract

The user supplied an Apple Notes reference and Figma-exported colours and geometry. Follow that reference explicitly, overriding the earlier warm glass treatment:

- Header gradient: `#FFD500` at the top → `#FFBA00` at the bottom.
- Header icon and title: white; 18 dp folder icon, 12 dp icon gap, 14.5 sp medium title.
- Header height: 44 dp with a 4 dp white perforation strip below. The whole header opens widget settings.
- Body: opaque `#FFFFFF`; black regular 13 sp row titles, readable grey metadata `#858585` (the supplied black-at-48% composite), 0.3 dp black-at-20% inset separators.
- Radius: 27 dp, clipped at the outer container. Content inset: 17 dp. Rows grow with text, with at least a 48 dp interaction area; check circles have a separate 44 dp action target.
- Light is the default. An optional Dark body retains a coordinated charcoal/white palette. Old Glass/Paper choices migrate visually to the reference white surface; their saved sources are preserved.
- Use the native platform font; do not bundle Apple's proprietary SF font files. Exact header hex colours are taken from the provided sRGB export rather than guessed from a screenshot.

## Content and scrolling

Three launcher gallery sizes remain: Small (2×2), Medium (4×2), Large (4×4). Each resizes in both directions. The launcher controls the physical cell dimensions. All compositions use a native, vertically scrollable `ListView`; no size caps the collection to the number of visible rows.

Recent notes exposes every eligible note, including five or ten when that many exist. Small widgets show fewer rows at once and scroll to the rest. Large widgets also retain the complete list. Never create fake rows to satisfy a minimum in a smaller library.

| Variation | Content and interaction |
| --- | --- |
| Recent notes | All live notes, newest edit first; tap a row to open it |
| Pinned notes | All live pinned notes |
| Collection | All notes in a selected collection and descendants |
| Single note | Scroll a selected note's title and plain-text excerpt; open for full content |
| Checklist | All check items from the selected note; text opens it, circle toggles it |
| Quote | Scroll a selected note's first Quote block; open the source note |
| Tasks | All pending tasks, earliest due first, optionally filtered by list; open or complete |
| Quick capture | New note, checklist and task actions |

No decorative widget motion. Do not compress text and targets to fit five rows into a tiny tile. At large system text sizes, rows grow; scrolling keeps the remaining content reachable. Media paths, Trash, archives and erased records stay out of the projection.

## Collection implementation

Android 12+ uses `RemoteCollectionItems` for the complete collection. Send one composition based on current launcher dimensions and refresh it when resized, avoiding three copies of the same rows. Android 10–11 uses `RemoteViewsService`/`RemoteViewsFactory` in a separate process reading an atomic, private widget snapshot. This avoids opening the editor library from a remote adapter callback. Stable IDs derive from note/block/action identity, never position. Updates can therefore retain a sensible scroll anchor.

Collection rows use fill-in intents on an explicit mutable pending-intent template. The destination is a private, transparent, preview-free widget activity. Completion uses the existing store and immutable revision token without bringing the editor forward. Note taps open the correct document through the normal navigation path. Reconfiguration, stale actions and deleted sources cannot mutate a different item. Keep direct per-row pending intents out of collection children: Android ignores that unsupported pattern.

Refresh after store changes and completion; legacy lazy collections also receive data-set notifications. Periodic refresh is requested at 30 minutes; Android controls scheduling. No network work or attachment bitmap decoding runs during widget rendering.

## Settings pattern

The phone Settings root has a compact centred title, profile row, and General / Library / Pebble Notes groups. Use `SettingsLinks`/`SettingsLinkedRow`: neutral 36 dp icon tiles, fixed icon and text columns, current values when space allows, 60 dp minimum rows, one inset divider between rows and 24 dp grouped corners. At large text sizes, allow labels to wrap and omit redundant inline values; keep their full accessible descriptions.

Preserve every existing preference, action and the About artwork. Grouping changes must not invent security, language or payment features from the supplied reference image.

## Research and provenance

The collection design follows Android's [collection-widget guide](https://developer.android.com/develop/ui/views/appwidgets/collections), [responsive layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts) and [configuration contract](https://developer.android.com/develop/ui/views/appwidgets/configuration). The Apple reference is the user's supplied recreation and [widget guidance](https://developer.apple.com/design/human-interface-guidelines/widgets).

Settings refinement follows the user's app-design.md and **unslop by Damir Sokolovsky (@heydamir)**, [source](https://github.com/heydamir/unslop), CC BY 4.0. The skill is installed locally for Codex; its web-specific suggestions are adapted to native Compose and the existing Pebble design system. The requested yellow widget gradient is an explicit user exception to the skill's default neutral-surface rule.
