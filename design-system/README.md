# Pebble design system

Version 1.0.0 · SwiftUI/AppKit on macOS · Jetpack Compose on Android

Pebble is a quiet place for notes, images and tasks. Its interface uses readable text, rounded surfaces, restrained translucent chrome and motion that follows the user's hand. This system is the default for future UI work. Explicit user direction takes precedence; a genuinely different interaction can extend the system with a documented reason.

## Start here

| Reference | Use it for |
| --- | --- |
| [Tokens](TOKENS.md) | Colours, typography, spacing, shapes, targets and motion |
| [Components](COMPONENTS.md) | Choosing an existing button, field, menu, card or navigation control |
| [Patterns](PATTERNS.md) | Page layouts, note editing, gestures, safe areas and shared behaviour |
| [Machine-readable tokens](tokens.json) | Shared numeric foundations and native generation |
| [Product rules](../UI_STYLE.md) | Pebble-specific requirements and exceptions |

## How to build with it

1. Choose a pattern and an existing component before inventing a new surface.
2. Read colours from the platform's semantic theme, and geometry/motion from `PebbleTokens` where a matching token exists. Preserve user typography and note style overrides.
3. Keep a feature's data mutation in the store. Components render state and expose actions; they must not create a competing persistence or undo system.
4. Include normal, pressed, selected, disabled, focused and destructive states as applicable. Give icon-only controls an accessible name and the full visible hit target.
5. Verify light/dark, narrow layouts, long text, keyboard/system insets, Calmer motion and recovery/undo for changes to editing behaviour.

## Native implementation

Existing components remain the reusable implementation; this is not a separate mock component library. The manifest generates checked-in Swift and Kotlin constants:

```sh
python3 scripts/design-tokens.py
python3 scripts/design-tokens.py --check
```

Generated files are [PebbleTokens.swift](../mac/Sources/PebbleTokens.swift) and [PebbleTokens.kt](../android/app/src/main/java/dev/leafnotes/PebbleTokens.kt). Change the manifest, regenerate both, and build both platforms. Do not edit generated files or silently change one platform's shared numeric value.

Foundation tokens are already used by desktop material buttons/dividers/icon controls and phone press feedback/action rows/chrome defaults. Other existing screens still contain local values. Adopt matching tokens when touching those screens; do not make a broad visual rewrite merely to remove literals. A reference token describes the intended default, not proof that every legacy screen is migrated.

Colours retain their existing platform adapters: desktop exported semantic JSON and `LeafPalette`, phone `LeafColors`, and per-note `NoteStyle`. Their alpha and platform differences are intentional. Do not flatten them into one fixed hex palette. The manifest lists these source files rather than creating a second colour authority.

## Changes and exceptions

Treat additions as system changes: add the token's name, value, unit and purpose; update component/pattern guidance; generate native constants; verify callers. Prefer semantic names for new component-specific values. Add a component only when an existing one cannot express the behaviour safely. Keep feature-specific selectors local until reused.

When a pattern intentionally differs, record its scope and reason here or in `UI_STYLE.md`. For example, a note's user-selected paper is content styling, while an action sheet uses the app's semantic surface. A transient action menu may repeat row dividers; a grouped settings card uses one divider between rows, never duplicate separators.

This document is the entry point for both humans and future coding agents. It covers native components and shared behaviour; no Figma file or published external library is required.
