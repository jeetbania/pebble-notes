# Layout and behaviour patterns

[Back to the system](README.md)

## Desktop frame

One seamless translucent window surrounds the sidebar and content. The outer radius is 22 points; the inner curve is 14 at an eight-point inset. Hiding the sidebar preserves equal content insets. Native traffic lights remain native. Do not add a second sidebar card or independent blur layer.

Sidebar entries use the same icon tile/selection language, optional right-aligned counts and contained drag targets. Toolbars group related controls into glass pills. Style is temporary, anchored to its trigger, rendered in the full content container and fully dismissible.

## Phone library and navigation

The header is fixed and clear, with safe padding for labels and actions. The note viewport begins below the title/count and clips its top corners inward at 44 dp. No note should pass behind the Notes label and no progressive blur should obscure the header. Backgrounds may extend behind system bars; the library viewport and editor viewport have different clipping needs.

The bottom dock is one persistent glass capsule plus a separate compose circle. Keep system gesture-area padding and keyboard clearance. Its Home, Tasks, Settings and Search icons use the current selected state. Ordinary page changes dissolve quickly; they do not enter from the side.

Search opens a focused full page, shows the keyboard and filters while typing. Results inherit List/Gallery and card-layout preferences. Cancel returns to the prior library state rather than replacing navigation history with arbitrary results.

## Note appearance and transition

A plain note is a seamless page. Adding a backdrop creates inset rounded paper; the backdrop appears around the top/sides of library previews while paper continues through the bottom. Preserve original media colours. Solid, vertical gradient and immutable image references travel with notes, sync and backups.

Phone status-bar tone follows the prominent image colour, solid colour, or visible top endpoint of the gradient. Use readable light/dark system icons. The editor opens through a rounded card-bounds reveal with synchronised backdrop dissolve; text is revealed at its natural scale. Closing reverses current progress, including interruptible edge-back. Calmer motion uses a simple dissolve. Do not replay library entrances after returning.

## Editor rhythm

Title and top-level body text align on phones. Put grips on the right, inside the document container. Toggle children alone indent. Markers align to the first line of wrapped text. Avoid empty add-child rows between toggle title and body.

Return after a heading creates body. Return in lists continues until empty. Return in an empty toggle child exits one nesting level after its parent's subtree. Type conversion preserves rich text and children; Quote gets a bar and Idea gets an icon/tinted surface. Table controls act on the selected row/column and maintain one row/column minimum.

## Physical interaction

Drag the entire block, subtree, folder row or task card as one object. Lift subtly; animate neighbours toward their new positions when the pointer crosses stable thresholds. Do not add a duplicate destination card. Preview local ordering and save a single undoable move on release. Keep drag coordinates independent of neighbour animation transforms. Preserve the grabbed object when direction changes.

Swipe actions are transient. Scrolling closes them, selection settles under the finger, and system back dismisses the current inner surface before navigating away. Keyboard interaction and native focus remain available alongside gestures.

## Settings, About and forms

A compact grouped settings index opens focused pages. Use consistent row separators, visible ghost buttons for reset/upload, and icon selectors where space is limited. Keep sheet titles and primary actions accessible while content scrolls. Do not add promotional copy to preference screens.

About has one version, the app icon/name, concise rows for local storage/optional Drive sync, notes/images/tasks, and history/Trash/backups, plus What's new and Replay welcome tour. Match this content across platforms while respecting native layout.

## Performance and accessibility

Use stable item identity, cached filtering/grouping and asynchronous bounded image decoding. Avoid saving every intermediate drag position, synchronously decoding wallpapers or rebuilding the whole editor each animation frame. High refresh preference asks for a supported rate up to 120 Hz; device power/thermal policies still apply.

Every required action has an accessible label and a full target; icon-only controls retain tooltips/names. Honour readable foregrounds, reduced motion/transparency, text preferences and safe insets. Motion conveys state, never delays access. Empty content is an empty state, not a perpetual loader.

For a new component, check the states in the catalog, light/dark, long/wrapped text, narrow desktop/phone bounds, keyboard, system back, persistence, undo and nested editor structures relevant to it. Use isolated libraries for destructive checks. Record concrete known exceptions rather than claiming all platforms have identical pixel values.
