# Pebble Notes 0.14.0 acceptance record

Desktop-first fixes requested on 4 October 2026. Mac build 15; Android build 16. Application identities, data paths and signing identities are retained.

## Changes

- Desktop onboarding uses a uniform background on every page, without a cropped gradient rectangle.
- Profile settings contain a local name, bio and picture. A persistent random accent and first initial form the fallback avatar. Settings fields use adaptive translucent surfaces. AGENTS.md directs future UI work to UI_STYLE.md.
- Task subtasks have a visible input; tags display as translucent colour pills. Photo captions reserve vertical padding independently of window size.
- Title Return focuses body; headings persist distinct sizes and weights even when initially empty. Return creates another block, headings return to body, lists continue, and toggles create a child. Option–Return keeps an internal line break. Both native editing implementations preserve semantic formatting.
- Markers and six-dot grips align to the first text line. Toggle headings and children have tighter grouped spacing.
- Whole blocks follow the pointer with a subtle highlight while peers reorder; toggles move with their children collapsed. Folder icons and labels render as one moving row.
- Block conversion, duplication and deletion stay inside the desktop editor. Toggle conversion preserves and reveals child content. Tables clip every corner after expansion.
- Selection actions use the captured full set, including Trash Select All; permanent deletion confirms the count.
- Tasks expose priority, Today, Tomorrow and custom scheduling actions. Time controls use faded rolling hour/minute/AM–PM columns on Mac and Android.

## Verification

- 19 core checks, 26 Mac checks and 18 Android instrumentation checks passed. Mac and Android builds succeeded; release history validation passed.
- Real desktop interaction in an isolated library: all four onboarding pages; profile editing, fallback colour, image selection and persistence across restart; light/dark colours; note title Return; three heading sizes and body transitions; bold/italic/strike; bullet/number/checklist conversion and continuation; toggle child creation/collapse/conversion; block drag from third to first; grouped folder drag; expanded 3×3 table corners; caption spacing; task tags, subtasks and rolling time saved and reopened; two-card Move to Trash and two-item Select All deletion count.
- Actual permanent purge of three selected notes and saved history was verified through the production store in an isolated automated test library. UI permanent-deletion confirmation was cancelled after verifying its count.
- Android instrumentation ran on Pixel 9 API 37.1 emulator, not the Vivo. Emulator GUI confirmed title Return to body, slash h1 suggestion/Return selection, visible heading weight/size and following regular body block. Native view updates now read the current block to avoid applying stale text during fast input.

## Remaining phone pass

The broader phone UI review and physical Vivo software-keyboard interaction checks remain separate work. The Vivo was disconnected during this pass. Android contains the shared formatting, Return and task scheduling changes; this record does not claim complete phone polish or a physical-phone test of 0.14.0. Desktop caption spacing, Profile and Appearance were also checked at the minimum 790×560 content size, alongside the wider-window checks above.
