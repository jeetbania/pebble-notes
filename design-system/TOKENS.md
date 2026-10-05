# Tokens

[Back to the system](README.md)

## Colour roles

Use semantic roles, with opacity applied at the point of use. User-selected note paper/backdrop/text colours are separate from the app theme.

| Role | Android source | macOS source / usage |
| --- | --- | --- |
| Page | `LocalLeafColors.current.page` | Native window background / `WindowMaterial` |
| Grouped surface | `paper` | `CardMaterial` or native neutral material |
| Editor canvas | `canvas`, overridden by `NoteStyle` | `NoteStyle.paper` / native text background |
| Neutral fill | `fill` | Primary colour with restrained opacity or material |
| Primary text | `text` | `Color.primary` / native label |
| Secondary text | `secondary` | `Color.secondary` |
| Tertiary / decorative text | `tertiary` | Primary at reduced opacity; avoid for required body copy |
| Accent / selection | `accent` | `LeafPalette.accent` |
| Destructive | `danger` | Semantic red / palette Red |
| Separator | `separator` or `text × dividerOpacity` | `Color.primary × dividerOpacity` |

The phone defaults are page `#F2F2F7` / `#000000`, paper `#FFFFFF` / `#1C1C1E`, canvas `#FFFFFF` / `#000000`, primary text black / white, and yellow accent `#FFCC00` / `#FFD600`. Secondary text retains alpha (`#993C3C43` / `#B2EBEBF5`). Accent choices are Yellow, Blue, Purple, Pink, Green and Orange, with adaptive theme values.

Desktop palette exports live in [Light.tokens.json](../assets/Light.tokens.json) and [Dark.tokens.json](../assets/Dark.tokens.json), bundled into Mac resources. Phone values live in [Colors.kt](../android/app/src/main/java/dev/leafnotes/Colors.kt). Never replace these with fixed grey text shared across themes.

Choose an ink colour visible against its actual composited surface. Accent is a selection signal, not a guarantee that white foreground text is legible. Check required text at 4.5:1 contrast and large text at 3:1. A custom note uses automatic readable ink unless a valid explicit ink colour is chosen. Newly adding a backdrop to a dark document supplies complementary light paper. Image status-bar tone comes from its prominent colour; a gradient uses its visible top endpoint. System icon contrast follows that tone.

## Typography

Use the native system font. Default note semantic sizes are shared; Android uses sp and macOS uses points. `Typography` / `TextMetrics` resolve user preferences and note scale. Do not bake sizes into stored rich text when a semantic style is intended.

| Note style | Token | Default | Preference range |
| --- | --- | --- | --- |
| Body | `bodySize` | 18 | 14–26 |
| Heading | `headingSize` | 21 | 17–34 |
| Subtitle | `subtitleSize` | 24 | 18–34 |
| Title | `titleSize` | 30 | 24–44 |

Headings are visibly heavier than body text. Phone UI labels use `Label`: line height 1.32 × size, tracking −0.7 sp at 28 sp or above and −0.15 sp below. Typical phone action text is 15–16 sp and page heading is 30 sp; desktop material actions are 13 points, medium. Required labels may wrap; summaries may truncate. Note titles wrap on phones.

## Geometry

Spacing scale: **4, 8, 12, 16, 20, 24, 32, 40, 48**, named `space4` … `space48`. Use logical points on macOS and dp on Android. A token does not replace safe-area or measured-content calculations.

| Token | Value | Use |
| --- | --- | --- |
| `desktopTarget` | 36 | Minimum desktop action bounds |
| `mobileTarget` | 48 | Default phone control target |
| `mobileCompactTarget` | 44 | Compact phone minimum, including block grips |
| `desktopFrameRadius` | 22 | Outer window curve |
| `desktopPaneRadius` | 14 | Inner curve at eight-point inset |
| `actionRadius` | 10 | Desktop material actions / phone icon tiles |
| `fieldRadius` | 12 | Rounded fields and ghost actions |
| `cardRadius` | 22 | Grouped panels / card material |
| `mobileViewportRadius` | 44 | Inward top clip below the library heading |
| `mobileDockHeight` | 58 | Capsule without system insets |
| `mobileComposeSize` | 52 | Separate compose circle |
| `sheetIconTile` / `sheetIconSize` | 32 / 19 | Phone action rows |
| `sheetDividerInset` | 44 | Row separator starts after icon and gap |

Capsules and circles use native capsule/circle shapes, not arbitrary numeric corner radii. A card's content and all added table cells must remain inside its clip. Phone gallery heights are bounded 220/280 dp in Masonry and 250 dp in equal Grid; maintain the selected layout in search results.

## Surfaces and states

- Press scale: `pressScale = 0.98`; disable spatial feedback in Calmer motion.
- Disabled chrome: `disabledOpacity = 0.35`; disable its action as well as its appearance.
- Desktop pressed foreground: `pressedOpacity = 0.65`.
- Desktop material action: fill 0.075, pressed fill 0.14, border 0.07 of primary.
- Action dividers: `dividerOpacity = 0.10`; phone width 1 dp, desktop 0.5 points.
- Translucency: use `leafGlass` on desktop, `frosted` inside `FrostedHost` on phones. Keep one owning backdrop; do not stack independent blur layers.
- CardMaterial: native material, adaptive white wash, fine stroke and soft shadow. Respect reduced transparency with an opaque adaptive surface.

## Motion

| Token / pattern | Default | Behaviour |
| --- | --- | --- |
| `feedbackMs` | 120 ms | Short pressed feedback |
| `pageFadeMs` | 140 ms | Ordinary phone page dissolve |
| `cardEnterMs` | 160 ms | Opacity only, once per note identity per session |
| `cardStaggerMs` | 18 ms | Cap after three cards; beyond first 16 show immediately |
| `noteMorphMs` | 300 ms | Rounded card bounds reveal, reverse from current progress |
| `sheetExitMs` | 180 ms | Complete dismissal before applying deferred sheet action |
| Navigation spring | damping 0.86, stiffness 520 | Finger tracking then restrained settle |
| Peer reorder spring | damping 1, stiffness 480 | Neighbours move without bounce |
| Press spring | damping 1, stiffness 900 | Brief feedback |

Compose spring stiffness and SwiftUI response are different native parameters. Preserve equivalent feel; do not copy stiffness into a SwiftUI response. Desktop uses restrained response around 0.24–0.34 seconds for panels/reorder. Keep lifted content separate from animated peers. Do not stretch editor text or add a delayed input lock to create motion.

Calmer motion and system Reduce Motion remove spatial travel and excessive spring response. System display scheduling owns frame timing. Up to 120 Hz is a preference capped to supported rates, not a timer-driven animation loop or guaranteed frame rate. Avoid synchronous image decoding, repeated filtering, repeated entrances and unnecessary state writes during motion.
