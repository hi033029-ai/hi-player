# Library feedback, compact controls and motion

- Removed the permanent all-files-access sentence from the library. The selected-video limitation notice remains where applicable; Android permission gates are unchanged.
- Separated background indexing from visible scan feedback. A first empty-library scan or explicit empty-library Scan request may show the circle while work is active. Cached-content refreshes and later periodic scans do not show a scanning banner. Feedback is cleared on completion, failure, denied access and cancellation. Background indexing is retained to discover new/deleted files.
- Shorter Folders/Videos tab labels, reduced visual chip padding and 18dp view/sort symbols, with 48dp touch slots retained. Folder rows use 14sp names, smaller icons and less unused spacing. System/app font scaling is not disabled or clamped to fit.
- Video/music pagers keep two neighbouring pages composed and use a 300ms eased snap/tap animation. Library snapshots remain off-main; no storage scan is triggered by a swipe. Existing scroll, queue and selection behavior remains.
- Theme palette colors interpolate over 360ms without replacing screens. Intermediate grey-on-grey labels use a contrast-safe black/white foreground. Compose respects system animator duration scale.

Source-only follow-up based on 1.0.80 (81); version and main are unchanged. No build, tests, screenshots or physical-device validation run for this follow-up. Added feedback-lifecycle and intermediate-contrast regression fixtures, not executed. Next approved validation must check 320/360dp and landscape, large fonts, selected-only access, repeated periodic scans, permission revocation, empty/manual refresh, fast swipes, theme switches/reduced motion, thumbnails and real playback continuity.
