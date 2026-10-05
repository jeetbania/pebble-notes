# Pebble Notes 0.15.0

Android build 23; Mac build 22. Application identities, libraries and signing keys are preserved.

## Note styles

The paintbrush opens a temporary Style popover on Mac and an anchored popup on Android. Document, Backdrop and Text rows open their colour pickers on demand. Each picker has automatic/reset choices, a shared palette and custom colours. The live miniature preview reflects all three layers. Click outside or use Back/Close to dismiss.

Document colour alone retains the seamless page layout. Selecting a backdrop creates an inset solid document card with a rounded outline and soft shadow. Backdrops support solid colours or a two-colour gradient. Library note and image previews retain the chosen document and surrounding backdrop colours. Reset style restores the existing app appearance.

Automatic text selects readable dark or light ink against an explicit document colour. Explicit text colours remain unchanged across app appearance modes. Mobile controls and system icons use the surrounding surface brightness. Original image colours remain unchanged.

An optional per-note style object travels through the existing revision, undo, backup and Google Drive sync paths. Existing notes need no migration. Update both devices before editing styled notes: older app models do not retain new style fields when saving a note. Image backdrops, cover images and font families are outside this release.

## Validation

- Native Mac build and Android app/test builds passed.
- 20 core checks passed, including style validation and revision round trips.
- 29 Mac smoke groups and 20 Android instrumentation groups passed, covering style persistence, editing, backups, undo/redo, contrast and existing note/task behavior.
- An isolated Mac library visually verified the framed gradient document, dark-paper readability, document-only seamless layout, library previews and temporary colour-picker dismissal.
- Android 0.15.0 launched in the emulator and its update screen was visible. Interactive Android style-popup visual review remains incomplete because the computer-use controller did not reliably target the emulator touch surface. No physical-phone review is claimed.
- Mac code signing and the release packages/update signature are verified before publication.
