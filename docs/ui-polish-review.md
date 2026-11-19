# Cards and themes UI polish

This source update builds on the 1.0.113 code. It has not been compiled, run on a device, or included in a new APK. The app version is unchanged.

## Changes

- Shared 18 dp card shapes and 12 dp thumbnail shapes across the video library, music, files, and settings.
- Refined System light/dark and Hi Player Blue palettes, explicit Material surface colors, and a consistent selection surface. Custom theme controls remain available.
- Larger text roles with matching line heights, two-line media titles, wrapping metadata, and 48 dp action areas on the updated cards.
- A rounded navigation bar with a soft selected indicator and tab selection semantics.
- Theme options preview the palette on small screen-shaped cards. Settings sections have leading icons, readable descriptions, and full-row switch interaction.
- Music cards stack artwork and details in narrow columns or at larger font sizes. Video thumbnails use aspect ratios and the list measures its available width.
- Nested folder grids calculate their columns from available width. Incomplete rows reserve exactly the missing columns, including correct full-width single-column rows.
- HDR-only thumbnail labels now say HDR rather than incorrectly claiming 4K HDR.

## Source review

- Reviewed the modified callbacks, navigation tab mappings, selection tags, partial-download presentation, and menu actions.
- Calculated contrast from the preset palette values against normal, elevated, and selected card surfaces. The lowest ratio among primary, secondary, tertiary, and accent text is 4.68:1. This does not evaluate artwork, transparency in custom themes, animation frames, or actual display rendering.
- Reviewed numeric text units and imports; `git diff --check` is clean.
- Playback engines, gestures, file operations, authentication, build workflows, signing, and version metadata are outside this change.

## Pending approved build and device review

Compile and run the existing regression checks, then review the updated screens at narrow phone, landscape, and tablet widths with default and enlarged fonts. Check long names, one-item and incomplete folder-grid rows, light/dark/custom themes, TalkBack selection announcements, menus, switches, reorder handles, and navigation with the mini player visible. Recheck playback and file actions before publishing the next APK.
