# System theme, custom appearance, folder playback and HDR touch fixes

Source-only follow-up on PR #14. Version and signing key unchanged. No Gradle build, tests or device validation performed for these changes.

- System is the first theme and the default for new/unset preferences and onboarding. Compose resolves it from the current system light/dark configuration. Existing explicitly saved themes retain their IDs and behavior.
- Shared contrast selection uses the actual composited fill instead of assuming selected labels are white. Applied to video/music category controls, browser segments, theme selection checks and Material accent colors.
- Custom appearance has full-width role cards, hex/opacity readouts, labeled 56dp swatch targets and visible selection checks. Opacity is previewed locally and persisted on slider release, not every movement. Editor labels remain readable even with low-contrast custom colors.
- Manual Next/Previous and automatic completion use the same folder-scoped policy. Full normalized parent paths distinguish folders with identical names. The existing displayed/saved library order is retained. At either end, stop; do not wrap, shuffle or jump to another folder. Streams and unresolved content-only files do not guess a local queue. Completion callbacks read the current state rather than a stale selected item.
- Removed the native surface's approximate invisible HDR rectangle and its touch-up/cancel callbacks. Only the actual bounded 48dp Compose HDR button opens settings; other surface playback gestures remain intact. Inactive HDR no longer runs a continuous glow animation.

## Added regression sources (not executed)
- SystemThemeContrastTest: defaults/order, both system modes, explicit theme preservation, opaque/translucent label contrast.
- FolderPlaybackQueueTest: folder boundaries, existing order, duplicate folder labels, absent current item, network/unresolved URIs and duplicates.

## Device acceptance required after build approval
Switch device appearance while System is selected; verify explicit Light/Dark remain fixed. Check custom cards at narrow widths and large text, including slider updates. Play a folder containing several files; test Next, Previous and completion at boundaries. Tap/swipe/cancel above and around HDR in portrait/landscape, controls hidden/visible and locked; only the displayed HDR button should open HDR controls.
