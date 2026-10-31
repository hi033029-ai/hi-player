# Responsive libraries and recoverable file moves

## Source changes
- Native library screens reserve safe horizontal/bottom window insets, including Android three-button navigation and landscape cutouts. Browser-owned inset handling stays separate.
- Search fills its available content width. Video/music categories scroll at full width; layout/sort actions occupy their own row rather than obscuring the trailing chip. Touch targets are at least 48dp; the bottom pill grows with text.
- Video sections and music categories use keyed HorizontalPager pages with one adjacent page precomposed. Data comes from existing cached/scanned libraries; swiping does not start a storage scan. Separate page scroll positions survive tab changes. Folder/selection Back takes precedence; video paging pauses inside a folder/tree or selection/confirmation.
- Music collection members come from precomputed sorted groups. The music playback surface stays outside the pager. Adjacent video pages do not request online metadata until active.
- Confirmed recycle actions hide pending paths and folder descendants in video/audio/file/archive/document views immediately. This is pending visibility, not a success assertion. Duplicate moves are rejected; failure/cancellation restores unprocessed rows; actual successful moves remain hidden.
- Audio rows now expose a confirmed Move to Recycle Bin action. A shared moving status and Cancel action appear above native bottom navigation; results are reported once across tabs.
- Recycle metadata is written and synced before source removal. Same-volume rename is preferred. Cross-volume files use a synced staging copy; cross-volume directory fallback is deliberately refused to avoid partially deleting a directory. No resolver.delete() fallback or storage-permission bypass is used. Inaccessible/scoped-storage items stay in the library with a recovery message.
- Real completed moves ask Android's media scanner to reconcile the old path. Restore releases the visibility mask. An existing recycle-bin index reloads hidden original paths on startup. No media content is permanently deleted by a normal recycle action.
- AndroidX's native splash remains only until persisted settings load. Returning users skip the extra Compose logo page, and the settings gate uses the current palette instead of a hardcoded white frame. Onboarding is unchanged for first launch.

## Validation status
Source-only change. No Gradle, compilation, lint, unit tests, APK build, main push/merge, or device validation has been run. Version and signing key are unchanged. RecycleBinVisibilityTest is added but not executed. Source delimiter review and git diff --check are not runtime validation.

## Required next approved build/device checks
- Compile/package with the new core-splashscreen dependency; run existing regression tests plus RecycleBinVisibilityTest.
- 320/360/411dp portrait, landscape/cutout, split screen, gesture AND three-button navigation; system font 100/150/200%; app text size 12–24sp. Check header/search/chips/actions and every bottom label.
- Swipe in both directions from empty space and list rows, rapid direction changes, chip jumps, folder Back, selection, long-press reorder, grid pinch, tab changes, rotation, and playback controls. Confirm no rescans on paging and positions retained.
- Cold/warm start in system light/dark and custom themes; permission/onboarding routes and external playback/browser intents. Observe actual first-frame behavior on a device.
- Video/audio/document/archive, folder descendants, large cross-volume file, permission denial, missing file, duplicate tap, immediate cancel, cancel mid-copy, partial batch failure, switch tabs while moving, restart, restore, and explicit permanent deletion.
- File access restrictions remain Android-enforced. SAF/content-only items without an accessible filesystem path are not silently permanently deleted; a dedicated document-provider recycle flow is outside this change.
