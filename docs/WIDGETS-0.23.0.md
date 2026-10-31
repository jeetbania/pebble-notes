# Android widgets · Pebble 0.23.0

## Delivered scope

Eight configurable Android home-screen widget variations, each supporting Small, Medium and Large compositions, with Glass, Paper and Midnight surfaces. Native RemoteViews, Compose setup, existing note/task persistence and sync. This does not add macOS WidgetKit extensions. App identity, library format, launcher icon and About artwork are unchanged.

Research and reusable component rules are in [the widget design-system reference](../design-system/WIDGETS.md). The initial plan was: establish the focused content families and resize behaviour; build the shared renderer; add explicit configuration and deep links; connect completion to revision-safe store mutations; test content states and rendered sizes; package a signed compatible update. All implementation stages are complete.

## Interface review scope and coverage

The requested `better-interface` skill was read. Recon included AGENTS.md, UI_STYLE.md, design-system/README.md, TOKENS.md and the native component patterns. The skill's six owning review skills are not installed at either local skill root. Its instructions require marking these domains Not reviewed rather than recreating their checks from memory.

| Domain | Evidence / coverage | Result |
| --- | --- | --- |
| Accessibility | `better-accessibility` unavailable | Not reviewed |
| Layout | `better-layout` unavailable | Not reviewed |
| Writing | `better-writing` unavailable | Not reviewed |
| Typography | `better-typography` unavailable | Not reviewed |
| Colour | `better-colors` unavailable | Not reviewed |
| UI polish | `better-ui` unavailable | Not reviewed |

No actionable interface findings are asserted under these unavailable domain skills. Independent implementation verification below is narrower than a holistic interface review.

## Verification

- Android offline debug app and instrumentation APKs build successfully.
- `adb shell am instrument -w dev.leafnotes.test/dev.leafnotes.PebbleSmoke` exercises an isolated library, leaving personal notes untouched.
- Widget projection excludes Trash, archive, folder records and erased sources; recent/pinned/collection/quote/task selections are checked.
- Checklist completion persists; repeated revision tokens are rejected; recurring task completion advances its date; all three providers register.
- All eight variations and empty states inflate in Small, Medium and Large layouts. Native checklist layouts are measured at minimum composition heights with Glass/Paper/Midnight and 100%/200% font scaling; row bounds and minimum touch heights are asserted.
- Actual native rendered PNGs of all variations are generated for visual inspection. Observed partial text lines and uneven check-circle sizing were corrected before release.
- A consumed creation intent is removed so activity recreation cannot create another note. Quick task capture opens the existing composer without saving an empty task on Cancel.
- Source/collection/task-list links are wired in the app; physical launcher tap-through has not been verified.

Not verified: Vivo launcher gallery/placement/resizing, physical battery scheduling, TalkBack traversal, and real wallpaper appearance. These require a physical launcher/device check. Glass is a high-opacity translucent gradient; Android launcher widgets do not provide the app's live backdrop blur.

## Review verdict

Approve for the completed implementation checks only. Accessibility, layout, writing, typography, colour and UI polish remain **Not reviewed** under `better-interface` because their owning skills are unavailable; no holistic UI approval is claimed.

## Installation

Install the APK as an update. Long-press the home screen → Widgets → Pebble Notes → choose Small, Medium or Large → select content and surface → Save widget. Tap its heading later to change the configuration. Existing notes stay in the current library and sync through the existing mechanism.
