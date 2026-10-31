# Compact toolbars and browser loading corrections

## Changes
- Video/music use the same measured single-row toolbar: scrollable category viewport uses remaining width, two fixed 48dp view/sort actions remain visible beside it, and categories are clipped to their own viewport rather than drawn under actions. Search remains available-width; native safe bottom insets stay intact.
- Browser normal loading has no URL/status title panel. Its thin activity segment travels back and forth above the address bar; WebView progress is not presented as a percentage.
- No universal generic webpage skeleton. Known mobile YouTube feed/watch routes get different approximate structures only until main-frame paint. Desktop, Shorts, arbitrary sites and unknown routes render their own HTML/loading UI without a guessed overlay. The browser cannot infer an unseen website's exact layout before receiving it, and a URL template is not a captured preview.
- Slow/offline recovery remains exceptional, compact and actionable; Stop is also available in the address bar.
- YouTube ad handling watches player subtree/control changes, metadata/duration/play events, SPA navigation and player replacement. Immediate queued checks are coalesced, with a 120ms fallback only during a recognized ad and a slower idle timer. Visible enabled Skip controls take priority; the same turn never seeks content after invoking Skip. Mute/rate restore is bound to the actual video element.
- Do not hide the whole ad-control module or remove enforcement dialogs/backdrops. Initial cosmetic styles retry if document-start runs before the root exists. WebViews lacking document-start scripts get a first-paint fallback injection instead of waiting solely for page-finished.

## Status
Source-only. Version remains 1.0.74 (75), main/APK unchanged. No Gradle, compile, lint, unit tests, JS fixture execution, build or native/device visual validation run for this correction. Only source inspection, delimiter checks and git diff --check. The Kotlin policy tests and tools/test_youtube_skipper.cjs are added for the next explicitly approved validation run.

## Next approved validation
- Compile/link and run existing suite plus BrowserLoadingPolicyTest; run node tools/test_youtube_skipper.cjs.
- Native screenshots at 320/360/411dp, landscape/split screen and 100/150/200% system text; verify both toolbars are a single row and categories scroll without covering actions. Verify existing bottom insets on three-button and gesture navigation.
- Browser first load/reload/redirect/Back/tab-switch/Stop/error/offline: YouTube feed versus watch, desktop mode, Shorts, and unrelated websites. Bar starts promptly, reverses and stops on completion/error/Stop; no normal loading title or universal page cover. Capture actual frames; source structure is not visual QA.
- Live m.youtube.com and desktop YouTube: preroll/midroll, Skip appearing/enabling after countdown, chained ads, unknown duration, SPA navigation and replaced video elements; content position/mute/rate must not be changed outside recognized ad state. Deterministic fixture success is not evidence of live-site ad removal. Server-side stitched ads, experiments and anti-block refusals may remain unsupported; no guarantee of zero delay or universal skipping.
