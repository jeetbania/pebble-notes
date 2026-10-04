# Pebble Notes 0.14.3

Mac build 18; Android build 19. This release addresses the desktop interaction feedback. Android receives the matching version and release notes; its interface is unchanged from 0.14.2. Application identities, update signing keys and libraries are preserved.

## Desktop changes

- Shared shortcut catalog powers the Navigate menu and Settings → Shortcuts keycaps. Cmd+S toggles the sidebar, Cmd+K searches, Cmd+N creates a note, Cmd+F opens note search, Cmd+[ and Cmd+] navigate, Cmd+1–4 switch library views, Cmd+Shift+N creates a collection and Cmd+Shift+F toggles formatting. Existing Settings and export shortcuts are listed too.
- The collapsed sidebar layout now has the same eight-point inset on all edges.
- Gallery notes can be dropped onto sidebar collections. The target highlights, the lifted card settles into the collection, and its existing revision is updated without copying the note.
- Empty-state artwork uses opaque light/dark card fills. Rear cards no longer show through the front cards on translucent desktop backgrounds.
- Whole task cards open note details; nested menus keep their actions. Back/Forward track actual library, collection, note and image routes and dim at history boundaries. Returning from a task preserves the task view's filters and layout.
- Kanban cards use a regular blurred material beneath their subtle tint.
- Block dragging previously saved and reordered the document repeatedly while its measured positions animated. It now uses a frozen starting snapshot and insertion thresholds, animates a local ordering preview with stable block identities, draws one noneditable lifted group and commits once on release. Reduced Motion removes the reorder spring. This follows Apple's drag-and-drop guidance without requiring newer OS-only reordering APIs: https://developer.apple.com/documentation/swiftui/drag-and-drop
- Table cells use native multiline text views: Return inserts a newline. Text grows rows naturally, and dragging a row's bottom boundary changes its minimum height. Column widths remain adjustable. Dimensions are local Mac layout preferences; cell text remains in the shared note document.

## Validation

- All 27 native smoke checks passed, including navigation history branching and boundaries, nested block groups, persistence, undo/redo, rich text, backup recovery and signed updater validation.
- Native Mac and Android APK builds succeeded.
- Native GUI checks used an isolated QA library: Cmd+S, Cmd+K, Cmd+N and view shortcuts; Settings keycaps; even collapsed frame; note drops to Work and Personal; task card padding opens details; Back/Forward restores details and the prior board filter; nested task menu remains usable; Kanban status drop persists; table Return preserves multiline text; row-boundary drag increases height; block reorder works downward and upward; empty-state occlusion checked in light and dark.
- Release-note consistency and whitespace checks passed.

No physical-phone checks were performed for this desktop-focused release. Existing Android limitations documented in 0.14.2 remain pending a phone interaction check. Mac minimum system version remains unchanged.
