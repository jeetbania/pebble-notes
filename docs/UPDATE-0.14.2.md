# Pebble Notes 0.14.2

Android build 18; Mac build 17. App identities, update keys and stored libraries remain unchanged.

## Changes

Android Tasks previously drew its fixed navigation row using a separate light frosted material and added another status-bar inset. The row now shares the page background and uses the root system inset once. This removes the white band between the status bar and task heading.

Both apps now use native, static ghost-card artwork with adaptive subtle strokes, pale purple details, soft shadows and contextual actions. Coverage:

| Empty view | Action |
| --- | --- |
| Notes, gallery, collections and tags | Create a note |
| Images | Create a note to add a photo |
| Checklists | Create a note with a checklist |
| Pinned notes | Browse notes |
| Search | Clear search |
| Task list and board | Create a task |
| Filtered task list and board | Show all tasks |
| Archive and Trash | Informational state |

A blank editor remains an editable writing surface; loading, errors and conflict review keep their existing distinct presentations. Decorations are hidden from accessibility and do not intercept input. They do not shimmer or suggest loading.

Android update sheets now use a transparent native dialog background, stronger whole-card opacity and increased blur. Mac release cards use one thicker material. Neither adds an opaque panel behind the text.

Writing defaults are body 18, heading 21, subtitle 24 and title 30. Explicit custom text sizes are preserved. Slash commands, add-block controls, table cells and captions are larger.

Kanban now uses separate cards on both apps: titles, short descriptions, tinted tag/priority/date chips, and compact list/attachment footers. Completion circles remain in List only. Android supports whole-card long-press lifting and horizontal edge scrolling, with column hover feedback and one floating card. Its list picker matches the desktop tray capsule. Gallery cards and thumbnails gain restrained gradients, borders and shadows, and the opening note panel clips both top corners.

## Validation

- Native Mac app and Android APK builds succeeded.
- All 26 Mac smoke checks passed using isolated libraries.
- All 18 Android instrumentation checks passed on the Pixel 9 API 37.1 emulator using isolated libraries.
- Emulator interaction: seamless light Tasks header; filtered Board ghost state; Show all tasks restores existing cards; empty gallery search and Clear search restore the library; light/dark release sheets; enlarged body and add-block controls; `/check` suggests Checklist with an icon and inset spacing.
- Mac isolated native visual preview: task/image artwork in light appearance and release card in dark appearance.
- Mac Kanban interaction in an isolated library: dragging a task from To Do into In Progress changed the status and column counts. Board metadata and tags were checked visually.
- Android Kanban preview: priority action produces a red High chip; list filter has the tray capsule; note opening has rounded corners; gallery cards show subtle fading and depth.
- Release-note consistency and whitespace checks passed.

The Vivo was unavailable; this release was checked on the emulator as requested. Physical-device blur, keyboard rendering and whole-card long-press dragging still need a follow-up check when the phone is available. The current computer-use drag control starts moving immediately and could not exercise Android’s required stationary long press; the gesture is implemented, but that interaction is not claimed as verified.
