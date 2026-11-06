# GeckoView migration progress

## Assumptions
- The task is to migrate Hi Browser to GeckoView while preserving existing browser behavior, settings, and Android 24–25 compatibility; the existing browser remains available until parity is verified.
- Work stays on the feature branch; website authentication remains independent of the app account and uses supported provider flows. No cookies or tokens are copied between engines.
- The user authorized a test APK after the migration is complete. Keep work on the feature branch; do not merge to main or claim a partial build contains the completed migration.

## Checklist
1. [x] Inventory browser functionality, build constraints, and existing Gecko work.
2. [ ] Resolve current Gecko dependency, ABI, Android-version and existing media-player compatibility without changing the release build prematurely.
3. [ ] Connect Gecko sessions, lifecycle, navigation, tabs, saved state and existing browser UI/settings.
4. [ ] Implement secure authentication routing, permissions, uploads, downloads, popups, browsing data and privacy isolation.
5. [ ] Implement filtering, web media, fullscreen, PiP and existing browser feature parity.
6. [ ] Run automated build/tests and verify real-device browser, media playback and authentication behavior.
7. [ ] Prepare a tested build and record changes and any outstanding blockers.

## Verified
- Earlier isolated GeckoView 159 probe: 76 targeted tests passed with compile SDK 37.2, Kotlin 2.4.20, Java 17 and min SDK 26; this is not a production build or runtime validation.
- Gecko MediaStore destination prototype: 10 transfer/destination tests passed with fake storage; not connected to the browser or download queue.
- In the isolated GeckoView 159 probe, 31 selected player, scanner and download-library unit tests passed after adapting a Robolectric storage fixture. This is not real playback or browser validation; the fixture change is not in the production branch.
- The shared address/search normalization patch was applied in a local working copy and all three targeted tests passed; the patch is not applied to the remote branch because the existing browser screen exceeds the GitHub connection request limit.
- A production-source-set regression test confirms the existing Media3 1.5.1 factory registers both bundled AV1 and FFmpeg audio renderers. The same test fails under the GeckoView 159 probe because AV1 is omitted.
- A real API 28 x86_64 emulator loaded a local HTML page using GeckoView 159. This is an isolated smoke test, not Hi Browser integration or website authentication.
- The Media3 1.11 bridge passed three registration-only Robolectric tests but **crashed during real AV1 playback on the emulator**. The 1.5.1 JNI expects `VideoDecoderOutputBuffer.decoderPrivate` as `int`; Media3 1.11 uses `long`. The bridge must not be shipped. The probe is isolated from the app build.

- Rebuilt the official Media3 1.11.1 dav1d AV1 and FFmpeg extensions for arm64-v8a, armeabi-v7a, x86 and x86_64; preserved the existing FFmpeg decoder set. The isolated probe resolves its Media3 artifacts to 1.11.1, with strict dependency constraints.
- On the API 28 x86_64 software emulator, isolated instrumentation tests successfully loaded Gecko HTML, decoded AV1 with dav1d and decoded E-AC-3 with FFmpeg. The other three ABIs were built, not runtime-tested. The pinned clean-fetch build script ran end to end, built both AARs in a fresh temporary project, and verified four-ABI packaging. A fresh isolated test APK compiled, but emulator installation failed twice while Android system_server was restarting under watchdog; these newly rebuilt AARs have not yet been runtime-tested.
- A separate full-app compile against Media3 1.11.1 failed on removed MP4 and GL APIs. A diagnostic-only copy built and passed 31 selected tests after temporary source alterations; those alterations are not a validated production fix and are not applied to the app.

- The experimental Gecko tab controller now routes reload, stop, back, forward and desktop-mode commands to the selected session. In a temporary app configured for GeckoView 159 and Media3 1.11.1, 23 registry/controller tests passed; these are fake-session unit tests, not browser UI/device validation.
- `patches/media3-111-player-candidate.patch` replaces the removed shader base with a portable implementation and replaces the unsafe inverse MP4 flag mask with explicit flags. The app compiled against both Media3 1.5.1 and 1.11.1, and a flag-value test passed on both; three existing player tests passed on 1.5.1. This is an unapplied candidate: the shader has not been rendered on a device, MP4 playback/seek and the changed meaning of bit 6 in Media3 1.11.1 have not been verified. Do not treat compilation as playback validation.

- Added an experimental adapter between Gecko tab metadata and the existing browser tab store, plus controller restoration of saved URLs and desktop mode. It excludes private tabs, clears invalid openers, and refuses URLs with known credential parameters. In a temporary app copy, 25 tab registry/controller/persistence tests and one Robolectric store round-trip test passed. This does not guarantee detection of every sensitive URL, and it is not wired to the shipping browser or tested with real Gecko sessions.

- In a disposable app copy, synthetic H.264/AAC and H.265/AAC MP4 extraction and seeking tests passed on the existing Media3 1.5.1 and on the 1.11.1 player candidate; the 1.11.1 flag-value test also passed. The candidate runtime dependency report resolved all 41 Media3 occurrences to 1.11.1. These are Robolectric parsing tests, not playback; they do not cover subtitles, HDR, fragmented MP4, editable/auxiliary tracks or real user media. The candidate remains unapplied. Reproduction is in `experiments/gecko-browser/player-extractor-probe/`.

- Added an experimental Gecko browser coordinator and Compose shell using the existing Hi Browser home page, toolbar and tab switcher. It connects session callbacks, tab selection, reload/stop/back, fresh-session Home, shortcuts and regular-tab persistence, with stale-session callback guards. In a disposable GeckoView 159/Media3 1.11.1 app copy, the source compiled and 34 targeted coordinator, tab, store and shell tests passed. The shell test uses a fake page/session; real Gecko UI, real sites and lifecycle have not been tested. This code is outside the production source set and is not routed from the app. Settings/menu/voice callbacks, downloads, permissions, popups, filtering, media and sign-in are not fully wired; no feature-parity claim is made.

- Experimental Gecko fullscreen callbacks now hide browser chrome and route Back to Gecko’s fullscreen exit; the security indicator uses Gecko-reported security instead of assuming every HTTPS URL is verified. Navigation and failures clear that state. In a disposable GeckoView 159/Media3 1.11.1 probe, 26 focused loading, controller, coordinator and Compose shell tests passed. Tests use fake sessions; real fullscreen, security event ordering, system bars and devices remain unverified. This remains outside the shipping app.

- Added an experimental website-consent dialog and Android runtime-permission launcher. They require explicit site approval, reject unsupported requests, check manifest declarations and current OS grants, and ignore late results after cancellation. The dialog can be mounted in the experimental shell. Tests cover consent, invalid/undeclared permissions, cancellation and stale results; no real site/device permission flow was tested. The shipping app has no CAMERA, RECORD_AUDIO or location declarations, so those requests currently deny rather than pretending to work. These components remain outside the app source set and are not a complete browser integration.

- The experimental coordinator now records successfully completed regular-tab visits in the existing browser history store and the shell shows local history suggestions only for non-private tabs. Known credential-bearing URLs are excluded, failures/cancellations are not recorded, and subsequent page-title updates refresh the entry. In the isolated Gecko 159 / Media3 1.11.1 app probe, 25 coordinator, store, loading, persistence and shell tests passed. These are fake-session and Robolectric tests; live sites, redirects, same-document history, clearing history with queued writes, and device behavior remain unverified. The shipping app still uses WebView.

## Blockers and risks
- GeckoView 159 resolves Media3 from 1.5.1 to 1.11.1. Inspection of the two Media3 renderer factories shows that 1.5.1 auto-loads the bundled `Libgav1VideoRenderer`, while 1.11.1 looks for `Libdav1dVideoRenderer` instead. The registration-only probe bridge crashes on real AV1 playback because of a JNI field-type mismatch. Matching dav1d and FFmpeg replacements were rebuilt and smoke-tested on x86_64 only. A candidate for the app API changes compiles against both Media3 versions but requires playback validation and review before integration, as do each shipped ABI and device playback.
- GeckoView requires Android 26 or newer; API 24–25 need a safe tested fallback.
- An API 28 software emulator was used for limited runtime checks in an earlier environment; no physical Android device is connected for hardware, sign-in or playback validation.
- The experimental Gecko components are outside the app source set and the current shipping browser remains WebView.
- The test probes do not prove decoder, Google sign-in, HLS or private-browsing behavior on devices; a safe browser cutover requires those checks and feature parity.
