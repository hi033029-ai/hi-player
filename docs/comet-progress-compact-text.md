# Comet progress and compact library controls

## Source changes
- Adapted the requested start/progress/finish/restart lifecycle to Compose: an active load survives repeated page-start callbacks, WebView progress never moves backward within that load, explicit address/reload/back/forward/retry starts a new generation, progress=100 animates to completion then fades. `onPageFinished` retains history/script work but no longer completes progress. Stop/error/home cancels instead of pretending success.
- A moving pixel highlight continues during network stalls. The fill follows reported WebView progress (it is not byte progress); no invented trickle percentage or fake completion. Completion/fade is cancelled on a new generation.
- Video/music category text is consistently 10sp, medium weight; view/sort menu labels are 11sp. Existing app/OS font scaling remains honored. Fixed 48dp action targets and one-row scrollable categories remain.
- Bundled script text is cached in memory and read ahead off the UI thread; subsequent tab setup/fallback injection reuses it. No website content or credentials are cached by this helper. This removes repeated asset reads, not remote server/network delays.
- YouTube mobile player roots and mobile Skip selectors added; style/hidden mutations and video timeupdate/canplay trigger checks. CSS-hidden controls are ignored. Skip cooldown cannot fall through into seeking. Only recognized ad state permits fast-forward; normal content remains untouched.

## Validation status
Source-only change. APK version unchanged. No compiler, Gradle, unit/JS fixture tests, APK build or device/live-site validation run for this branch. Added progress lifecycle tests and expanded deterministic mobile skipper fixtures for the next approved validation. No claim of measured load-time improvement or guaranteed YouTube ad skipping.

## Next approved validation
Compile and run BrowserProgressStateTest, BrowserLoadingPolicyTest and existing targeted regressions; run tools/test_youtube_skipper.cjs. Check actual WebView redirects, progress without page-start, repeated finish, rapid navigation during fade, back/forward, tab switching, stop/retry, offline and larger fonts on narrow screens. Check mobile/desktop YouTube Skip appearance, countdown-to-enabled state, SPA player replacement, ad-to-content transition and original mute/rate restoration on real sites. Sites can change player markup and server-stitched ads cannot be assumed skippable.
