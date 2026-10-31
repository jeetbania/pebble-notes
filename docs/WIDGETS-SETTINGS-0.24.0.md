# Scrollable widgets and phone Settings · 0.24.0

## Scope and direction

The user's request covers Android widgets and the phone Settings flow. Existing note libraries, identities, sync, preferences and About artwork are preserved. Mac receives a companion version bump, without a new widget extension or Settings redesign.

Direction, using the user's app-design.md and installed unslop skill:

- Product: quick access to personal notes and tasks, with predictable library preferences.
- Character: quiet, legible, direct.
- World: paper and ink; the supplied Apple Notes widget recreation determines the widget colour treatment.
- Moments: one yellow widget header; the user's existing profile avatar remains the small personal element in Settings.
- Signature: the folder header and perforation line over white paper; consistent neutral icon tiles for Settings destinations.

The user already supplied the desired direction and asked for implementation, so no additional design-approval pause was needed. The reference image's unrelated security/payment features were not added.

## Audit and implemented fixes

| Priority | Evidence in the previous implementation | User consequence | Change |
| --- | --- | --- | --- |
| High | Widgets.kt truncated the rendered rows to 1/2/4 in a LinearLayout | Most notes could never be reached from a widget | Full native ListView collection, stable identities, per-row fill-in intents |
| High | Original verification inflated static rows without a real AppWidgetHost | Launcher behaviour and actual taps were unverified | Opt-in native host tests exercise real adapters, scrolling, pending intents and configuration |
| Medium | Beige translucent widget surface and small plus action diverged from the supplied reference | The result did not resemble the requested Apple Notes design | Supplied #FFD500 → #FFBA00 header, white icon/type/paper, black titles, exact export-derived metadata grey, rounded clipping and perforation |
| Medium | Large title, tall profile card and generous top padding in Settings | Too few meaningful choices visible at entry | Compact centred title, smaller profile row, consistent destination rows and stronger group headings |
| Medium | Accent-yellow Settings icons on white, without aligned value columns | Weak icon legibility and poor scanning | Neutral icon tiles, aligned labels/current values, inset separators; wrapping and stacked choices at large type |

## Implementation notes

All widget sizes include the full matching collection. A small tile cannot show five readable 44-dp targets simultaneously; scrolling exposes five, ten and more without shrinking content. No notes are invented in libraries with fewer items.

Current Android versions receive one RemoteCollectionItems composition, refreshed on resize. The 220-note fixture's largest measured payload is recorded in the test log. Avoiding three copies of the collection prevents unnecessary binder traffic. Android 10–11 retains a native service/factory fallback using an atomic private snapshot in a separate process. Cache data is removed with its widget and is not part of synced note styling.

The widget header opens configuration. The setup preview is itself hosted in AppWidgetHostView. Note rows open the corresponding document. Checklist/task circles use a private transparent activity, preserve revision tokens and do not bring the editor forward for completion. Old or conflicting revision tokens cannot overwrite newer content.

Widget note titles and excerpts have generous transport-safe bounds without splitting emoji. Opening the note always accesses the original full content. Attachment paths, archived/deleted/erased records and folder markers are excluded.

Settings retains every existing action. General contains appearance, writing, layout and clipboard choices; Library contains sync/backups; Pebble Notes contains updates and help/about destinations. Root current values remain accessible when visually omitted at larger type. At 200% text, selectors become stacked choices and the accent palette uses fewer columns. The About logo is unchanged.

## Verification and limits

See the committed ANDROID-WIDGET-TEST-RESULTS-0.24.0.txt for exact results. Tests use isolated fixture libraries and opt-in test-host code, excluded from the normal shipped APK. Real AppWidgetHost tests cover all three sizes, full-list scrolling, a scrolled row opening the correct note, the final item in a 221-note library, header configuration and its live preview, check completion, the remaining content families and Quick Capture. Core storage checks cover stale taps, recurrence, hidden sources, snapshot recovery and Unicode. Settings is rendered in light/dark and at 320-dp width with 200% text; Compose semantics actions verify navigation and preference saving.

The current emulator is a future Android API 37 beta. Physical Vivo launcher behaviour, older Android 10–11 host binding, manual TalkBack traversal and battery scheduling are not verified. The Android 10–11 snapshot factory is exercised directly, but that is not a substitute for a legacy launcher/device test. No usability-study claim is made.

The requested better-interface skill was read. Its six domain dependencies (better-accessibility, better-layout, better-writing, better-typography, better-colors and better-ui) are unavailable locally, so those skill domains remain **Not reviewed**. Independent implementation and unslop checks above are not a holistic better-interface approval.

## Provenance

Settings refinement applies the user's app-design.md and **unslop by Damir Sokolovsky (@heydamir)**, [source](https://github.com/heydamir/unslop), CC BY 4.0. Unslop is installed at ~/.codex/skills/unslop and becomes discoverable next turn. Its instructions were read directly for this implementation. The user's specific widget gradient and existing Pebble branding take precedence over generic skill suggestions.

The supplied Figma recreation provides the sRGB gradient, body/header colours, text roles, separators and radius. Native Android fonts and launcher dimensions differ from Apple devices; the colours are literal exported values rather than a claim of identical physical pixels across platforms. Framework work follows Android's [collection widget guidance](https://developer.android.com/develop/ui/views/appwidgets/collections).
