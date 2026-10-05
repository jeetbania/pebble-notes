# Pebble Notes project instructions

Before changing UI, read [UI_STYLE.md](UI_STYLE.md) and follow it. Keep this guide current when the user changes design rules. Use isolated test libraries for destructive or editor interaction checks. Preserve application identities, libraries and release signing keys. Never commit private credentials or signing keys.

For UI work, also read [design-system/README.md](design-system/README.md) and the relevant token/component/pattern reference. Reuse existing components and shared `PebbleTokens` by default; extend them only when the requested interaction needs a different pattern. Keep the design-system documentation current. After changing design-system/tokens.json, run scripts/design-tokens.py and scripts/design-tokens.py --check; build both native apps when generated or shared implementation changes.
