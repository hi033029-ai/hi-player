# Stable library selection and scrolling

Source-only follow-up on PR #14; version and signing key unchanged. No compilation, Gradle tests, APK packaging or device testing performed.

## Changes
- Video lists/grids keep the same keyed controls item during selection. The existing folder/status slot has a constant 64dp height and swaps its title for icon actions; no second app bar is inserted and the global header is not removed.
- Selection has accessible icons for exit, select all, deselect all, properties and delete, plus a count. Empty selection disables properties/delete. Back cancels selection before leaving tree folders.
- Video reordering now has a dedicated grip instead of competing with the entire card's long-press selection gesture. Its slot stays the same size during selection; dragging is disabled while selecting.
- Video search is shown only at the library root, not inside flat or tree folders. A previous search is cleared on entering a folder. Tree-folder selection and Select all are limited to its directly displayed videos.
- File Manager no longer inserts a separate selection app bar or detached action row. Folder titles and selection actions share the same 64dp slot in the root and folder views.
- The storage overview is a keyed item in the root file list rather than a fixed sibling above it. It scrolls away naturally, without hiding/unmounting it in response to scroll callbacks. Root folder title was consolidated above its sections.

## Regression sources added, not executed
`LibrarySelectionLayoutTest`: file row position before/after selection, icon callbacks and fixed toolbar height, storage overview scrolling off screen in a long root list.

## Device acceptance after build approval
Long-press videos in list/grid (including after scrolling), toggle multiple items and exit selection: no viewport jump or automatic drag. Verify title replacement and icon callbacks in video folders, tree folders and File Manager. Confirm storage scrolls out of view and returns when scrolling to the top. Verify video search appears at root only and hidden searches do not filter a folder. Test small screens, large text, dark/light themes, deletion confirmation/cancel and back navigation. Confirm the dedicated video grip still reorders the displayed library when not selecting.
