# Pebble interface rules

Read this before changing any user-facing UI. The user’s explicit direction takes precedence.

- A window has one seamless translucent frame. Sidebars inherit it; never draw a separate sidebar card or blur layer. Inner content can use a stronger neutral material.
- Inputs are visible, spacious rounded fields: semantic primary text, secondary placeholders, subtle neutral fill and border. Never use the platform’s opaque black rounded text-field default inside glass settings.
- Buttons use restrained translucent neutral surfaces. Dividers use primary at roughly 8–10% opacity. All colours adapt to light and dark; never hard-code gray text for both.
- Tags use tinted translucent capsules. Accent choices are deliberate; maintain readable foregrounds.
- Profile settings belong in Profile. Names and profile pictures are local unless an explicit sync feature is implemented. Avatar fallback uses a persisted colour and first letter.
- Clickable text has at least a 36-point desktop target; phone targets at least 44–48 dp. Caption areas reserve top and bottom padding at every window size.
- Do not place a gradient in a smaller rectangular container where it exposes hard edges. Desktop onboarding may omit the background gradient.
- Text hierarchy combines size and weight. Heading 1/2/3 are distinct and semibold. Return after a title or heading begins body text; Option–Return inserts an internal line break. Lists continue until empty; toggles insert an indented body child.
- Block markers and six-dot grips align to the text line, vertically centred for one line and aligned to the first line for wrapped content. Toggle children alone are indented; top-level toggles align to other blocks.
- Reordering tracks one grouped object with the pointer. Highlight the grabbed object subtly; peers move as it crosses them. No duplicate destination preview. Apply transforms to the whole folder row, never icon and text independently.
- Block options stay inside the window and offer type conversion, duplication and deletion. Preserve text and nested children when changing type; converting a toggle must not hide its children.
- Tables clip their complete content at all four corners, including after rows/columns are added.
- Selection actions operate on the entire selected set. Permanent deletion confirms the count and captures the full set before mutation.
- Playtest all affected controls in an isolated library, including empty blocks, wrapping, nested toggles, persistence, light/dark appearance and narrow windows. Automated checks supplement real interaction, not replace it.

- Mobile action buttons use capsule or circular shapes; fields, cards and menu rows retain their appropriate rounded rectangles. Do not add small page/brand labels to the top-right navigation area.
- Mobile home uses a purple glow behind cards with a long, smooth falloff and a paler light-mode variant. Status/navigation bars blend with the current page and use readable system icons. Scrolled content passes behind a blurred, faded header; header labels remain crisp.
- Mobile settings starts with a compact grouped index and opens focused inner pages. Avoid promotional paragraphs and redundant explanations. Sheet titles and primary actions remain visible while the form scrolls, with space above the keyboard and system gesture area.

- Fixed mobile task navigation uses the page surface, with no separate frosted strip or duplicate system inset. Glass release cards have one strong blurred material and a transparent dialog window; never add an opaque slab behind the copy.
- Empty states use static ghost previews with subtle adaptive strokes, soft shadows and a contextual action. Cover task list/board, notes/gallery, images, search, pinned notes, checklists, collections, archive and trash. Do not use shimmering loading skeletons for empty content.
- Default note typography is body 18, heading 21, subtitle 24, title 30; preserve explicit custom sizes. Editor commands, captions and add-block suggestions scale up with the writing UI.

- Kanban cards omit the list completion circle. Use a clear title, short description, coloured tag/priority/date chips and a compact list/attachment footer. Whole-card long press lifts one object above columns; edge scrolling reaches distant states. Show column hover feedback without a destination card preview. Mobile list filters use the same tray icon and capsule material as desktop. Mobile note openings clip their complete panel to rounded top corners; card and thumbnail depth uses restrained gradients, strokes and shadows without altering original image colours.

- Desktop shortcuts are declared in one shared catalog and shown as keycaps in Settings → Shortcuts. Command-S toggles the sidebar because notes save automatically. Navigation arrows use actual history, never an arbitrary recent note; unavailable arrows are disabled and visibly subdued.
- With the sidebar hidden, the content pane retains equal frame insets on all four sides. Notes can be dropped onto sidebar collections with a tinted target and a single pointer-following preview.
- Desktop ghost artwork uses opaque adaptive card surfaces so stacked previews occlude each other. Kanban cards use a blurred material, including while lifted. The whole task surface opens details; nested controls retain their own actions.
- Block dragging previews local ordering against stable insertion thresholds, keeps the lifted preview independent of peer animations, and saves one undoable reorder on release. Table cells accept multiline text; column and row boundaries resize width and height, and rows grow to fit their content.
