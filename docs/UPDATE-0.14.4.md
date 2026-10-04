# Pebble Notes 0.14.4

Android build 20; Mac build 19. This release polishes the native Android interface. Mac receives the matching version and release notes; its interface remains as shipped in 0.14.3. App identities, libraries and signing keys are preserved.

## Changes

- Task options replace the long repeated action list with coloured status and priority icon strips, short date pills, dividers and a compact action toolbar. Controls retain accessibility labels and selected-state semantics.
- Task details share the icon strips, with one current-value caption below each. The fixed Save task pill remains visible while the form scrolls.
- Notes are horizontal full-page transitions without a lifted rounded rim or shadow. Native text views remove extra font padding and minimum height; contextual block gaps group headings and children more tightly.
- The inline Add inside toggle action is removed. Return on a toggle still creates and focuses a child; existing children and collapse behaviour remain intact.
- Shared sheets coordinate opacity, vertical translation, window dimming and blur with one animation progress value. Platform window animations are disabled to prevent competing effects. Close, outside-tap and Back animate out, and task option actions complete the exit before updating or opening the next surface. Calmer motion removes transition duration.
- Profile settings drop the Name & picture subtitle and use Name and Bio field labels.
- Android empty-state artwork uses opaque adaptive card fills without rear-card opacity, matching the desktop occlusion fix.

Animation approach reviewed against official Compose guidance: https://developer.android.com/develop/ui/compose/animation/composables-modifiers

## Validation

- All 18 Android smoke checks passed on the Pixel 9 API 37.1 emulator using the final APK. Coverage includes task persistence/status/repeats, rich native text/JNI, heading Return, nested toggle groups, backup restore/export, Trash and signed update validation.
- Native Mac and Android builds succeeded. Mac signing verification and release-note consistency checks passed.
- Emulator GUI checks covered light and dark task options, creating and saving a task, priority persistence, status persistence after animated dismissal, task detail control fit and fixed Save footer, shorter profile index copy, compact starter-note spacing, flush note page presentation and opaque empty-task artwork in light mode. Outside-tap dismissal also returned cleanly to the task list.
- Physical Vivo testing remains pending; no physical-device performance or frame-timing claim is made. Generic sheet action callbacks outside task options may still immediately switch their owning surface; the coordinated shared entrance and normal dismissal apply globally.
