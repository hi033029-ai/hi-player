# UI and performance source pass

Source-only work on top of draft PR #14. Version remains 1.0.68 (69). No compilation, benchmarks, automated tests, APK generation, main merge or release performed. No claim of universal device compatibility or measured speed improvement.

## Targeted changes
- Isolated high-frequency music position/lyric subscriptions inside small playback composables; the main app shell and music library no longer read those tick flows.
- Lifecycle-aware UI flow collection for main shell, video/music libraries, browser download state and file manager. This does not stop background scan, download or playback services.
- Moved video filtering, sorting, folder-tree/group transforms, continue-watching mapping, saved-order hydration and refresh merges to background dispatchers. Saved display order is applied once in the filtered flow rather than during Home composition.
- Music search/sort/group transformation is a pure helper invoked on Dispatchers.Default. Grid reordering receives the lazy item index directly instead of scanning indexOf per row. Path/URI-based music keys prevent duplicate MediaStore-zero row reuse; tree video rows also use stable path/URI keys.
- Shared memory-bounded, sampled audio artwork cache with serialized extraction, temporary failure backoff and trim callbacks. Art requests no longer fully decode embedded/album pictures independently per card. No remote media retriever calls or account credential forwarding; remote image URLs remain Coil-managed.
- Video thumbnail budget now scales with heap instead of a fixed 24MB minimum. Added trim response, short failure backoff, cancellation checks and cleanup of discarded black candidate frames. Remote video URLs cannot block the serial local extractor. Revision keys refresh changed files and coarsen growing-download invalidation.
- Remote image requests are remembered and avoid repeated crossfade work. Removed eager catalog lookups across the whole scanned library; visible cards still request metadata on demand.
- Polished music rows with consistent padding/surfaces, selected-only outlining, bounded metadata text, readable small-screen video thumbnail sizing, shorter Files navigation label and opaque high-contrast navigation surface. Removed redundant header/thumbnail outlines. Format badges no longer invent MP3 bitrate or FLAC high-resolution claims. Theme-derived color scheme is remembered.

## Checks and validation limits
Only source review and git diff --check performed. Added ArtworkMemoryPolicyTest and AudioLibraryPresentationTest source, not executed. Existing source-only Cast/music/navigation changes also still require compilation and device validation; the previous release's 81-test result does not apply.

Before release: run compilation and regression tests, then measure startup, scroll jank, bitmap/native heap, frame decoding and library filtering with small/large libraries on low-memory and recent phones. Exercise Android 7/API 24 fallback, MediaStore and SAF files, partial downloads, repeated tab switching, memory pressure, lifecycle/background playback, font scaling, compact screens/tablets and all themes. Fullscreen artwork sharpness versus cache size should be reviewed on larger displays. Older hardware/codec support can impose limits; do not promise it works perfectly on every device.
