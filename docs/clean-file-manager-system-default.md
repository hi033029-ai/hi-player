# Compact File Manager and System-default theme

Source-only follow-up to user screenshots after testing v1.0.69 (70). No new build, compilation, tests or release performed. Version/signing key unchanged.

- Removed the redundant File Manager root breadcrumb that displayed the `/storage/emulated/0` basename as `0`, a back arrow and an item count. Actual nested folders keep a useful Back button and folder name, without the duplicate folder icon/count.
- Removed the large Extractable Archives, Documents & Text Files, and Android Folders & Storage Explorer section panels and separate Show/Hide rows. Existing storage-summary shortcuts now switch the single root list between archives, documents and folders; file operations and selection remain available.
- Root selection replaces the storage title in its existing fixed-height slot rather than introducing a new toolbar row.
- Generic files, documents and archives show selection circles only while selecting, not merely because long-press is supported. Selection checkmarks contrast with the theme accent.
- Reduced repeated margins and row gaps; removed the extra progress-track outline/end-marker dot. Video lists have smaller top/bottom padding and no duplicate Close Folder action beside Back.
- System remains first in the theme picker. A one-time DataStore migration makes it active for existing installations too, without deleting custom colors or unrelated settings. Later explicit theme selections are retained; they are not reset on every launch.

Regression sources added (not run): FileManagerCleanBrowsingTest (no root numeric breadcrumb/duplicate panels; selection circles hidden during browsing) and SystemThemeDefaultMigrationTest (one-time reset, preserve colors, respect later choices).

Device checks after approval: upgrade a previously themed installation, verify System follows device light/dark mode, then choose another theme and restart. Browse root and Download: no `0` breadcrumb at root or empty selection circles outside selection mode. Check Archives/Docs/Folder shortcuts, empty results, long-press/multiple selection, back navigation, deletion confirmation and extraction. Scroll storage summary naturally out of view; ensure reduced video spacing doesn't overlap the bottom navigation.
