# Component catalog

[Back to the system](README.md)

Use these implementations before adding a new control. The platforms share roles and behaviour, while preserving native input and accessibility.

## Buttons and action rows

| Component role | macOS implementation | Android implementation | Rules |
| --- | --- | --- | --- |
| Icon chrome | `GlassIcon`, grouped with `leafGlass` | `ChromeButton` | Accessible label, whole visible target, subdued disabled state |
| Grouped chrome | `leafGlass(in: Capsule())` | `ChromePill` | One material; no redundant hover capsules |
| Standard material action | `MaterialActionStyle` | `GhostAction` for labelled neutral actions | Visible fill, border and balanced padding |
| Reset / upload | `GhostButtonStyle` | `GhostAction` | A real button, not a plain underlined/text settings row |
| Low-emphasis action | `SoftButtonStyle` | `Pressable` inside a suitable surface | Caller supplies geometry and semantic role; do not use a bare tiny label |
| Sidebar entry | `SidebarButtonStyle` | `FolderRow` / `CollectionRows` | Selected state, right-aligned optional counts, whole row target |
| Option action | Native context menu / menu buttons | `SheetRow` inside `IosSheet` | Icon, readable label, subtle divider, destructive semantic tint |
| Compact secondary action | Standard native control | `SheetRowCompact` | Do not use for a primary or destructive action |

Desktop shared controls live in [Design.swift](../mac/Sources/Design.swift); ghost/upload actions in [Wallpapers.swift](../mac/Sources/Wallpapers.swift). Phone controls live in [LeafUI.kt](../android/app/src/main/java/dev/leafnotes/LeafUI.kt). `Pressable` supplies feedback and click semantics, not a complete visual button or minimum size. Some legacy desktop low-emphasis styles have smaller intrinsic bounds: new callers must supply the 36-point target.

**State contract**

| State | Required treatment |
| --- | --- |
| Normal | Semantic text, chosen surface and full hit shape |
| Hover (pointer) | Restrained fill/foreground change; no layout shift |
| Pressed | Brief scale or foreground response; action fires once |
| Selected | Persistent accent/current-value state independent of press |
| Disabled | Action unavailable; visibly subdued, no misleading activation |
| Focused | Native keyboard focus and an accessible name |
| Destructive | Semantic danger tint and recoverable Delete where supported; irreversible actions confirm the captured set |
| Busy | Preserve geometry; expose progress and prevent duplicate submissions |

## Fields and selectors

- Android: reuse `MobileField`, `IosSegments`, `PillSlider`, `TypographySettings` and `NoteSizeControl`. A field needs a visible fill/stroke, primary input, secondary placeholder, accent cursor, and explicit multiline/single-line intent. Put multiline content at the top.
- macOS: use native text input with the rounded neutral treatment in Settings/Style, and `AdaptiveSlider` for preference sliders. Do not insert the opaque black rounded text-field default into glass.
- Segments represent mutually exclusive choices. Animate one selection surface; allow gestures where already supported and commit the final choice. Keep the current value intelligible without colour alone.
- Native editors (`LeafTextView` / phone `EditText` adapters) own rich text selection and keyboard actions. Do not substitute a generic field for the document editor.

## Menus and sheets

Android `IosSheet` owns presentation, dim/material progress, dismissal, system back and keyboard clearance. `SheetRow` owns an icon tile, label and low-opacity inset separator. `LocalSheetAction` defers an action until dismissal finishes. Grouped Settings pages have one separator between rows; do not combine an external divider and a row's own divider.

macOS `PanePresentation` / `GlassDialog` handle contained overlays, `NoteStyleButton` anchors Style inside the full content pane, and native menus retain AppKit tracking. Do not replace native menu items with globally observed custom views. Cross/close, Escape/system back and outside dismissal must target the correct owning presentation.

## Cards, lists and empty states

- Desktop `CardMaterial` is the adaptive material foundation. Note cards render `NoteStyle` and original media; task cards retain their own title/chips/footer hierarchy.
- Phone `GalleryCard` and library row implementations in LeafUI render the same note records. Use bounded Masonry or equal Grid from preferences, not arbitrary height per wallpaper. List swipe actions return to rest when scrolling begins.
- `MediaImage` and wallpaper helpers own loading/cache/clip behaviour. Thumbnails must not decode original-size media on the main thread.
- Empty states use existing static ghost artwork with opaque adaptive stacked surfaces and a relevant action. They are not loading skeletons.

## Navigation and editor blocks

- `MobileDock`: Home, Tasks, Settings and Search inside one capsule; compose is a separate circle. The selection follows the navigation gesture and settles at the destination. Search owns a focused page and live results with the current library layout.
- Desktop sidebar and history controls remain native; disabled history arrows indicate real availability. Counts are optional and align right.
- `DocumentBlocks` / desktop `DocumentEditor`: semantic text, checklist, toggle, quote, idea, divider, image/file and table blocks. Reuse store mutations to preserve children, undo and sync.
- `ReorderGrip` / `reorderTarget`: move the whole object; keep stable sibling/subtree ordering. Phone grip is on the right and in a 44 dp target.
- `MobileTable`: compact aligned multiline cells, clipped horizontal scrolling, row/column handles and insert/delete menus. Keep at least one row and column. Desktop table interaction retains its resize behaviour.

## Example composition

```kotlin
val colours = LocalLeafColors.current
GhostAction("Reset text sizes", "undo") { resetSizes() }
IosSheet("Options", onDismiss) {
    SheetRow("Duplicate", "copy") { duplicate() }
    SheetRow("Delete", "trash", tint = colours.danger) { moveToTrash() }
}
```

```swift
Button("Reset text sizes", action: resetSizes)
    .buttonStyle(GhostButtonStyle())
GlassIcon(icon: "magnifyingglass", label: "Search notes", action: openSearch)
```

These show visual composition only. Use each screen's existing dismissal/action dispatch and store APIs; do not introduce duplicate persistence in a component.
