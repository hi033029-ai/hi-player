# GeckoView migration progress

## Assumptions
- The task is to migrate Hi Browser to GeckoView while preserving existing browser behavior, settings, and Android 24–25 compatibility; the existing browser remains available until parity is verified.
- Work stays on the feature branch; website authentication remains independent of the app account and uses supported provider flows. No cookies or tokens are copied between engines.
- Existing approved test/build access covers local verification. Do not publish an APK or merge to main without the approval required by project rules.

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
- On the API 28 x86_64 software emulator, isolated instrumentation tests successfully loaded Gecko HTML, decoded AV1 with dav1d and decoded E-AC-3 with FFmpeg. The other three ABIs were built, not runtime-tested. The pinned clean-fetch build script ran end to end, built both AARs in a fresh temporary project, and verified four-ABI packaging. These newly rebuilt AARs have not yet been runtime-tested.
- A separate full-app compile against Media3 1.11.1 failed on removed MP4 and GL APIs. A diagnostic-only copy built and passed 31 selected tests after temporary source alterations; those alterations are not a validated production fix and are not applied to the app.

## Blockers and risks
- GeckoView 159 resolves Media3 from 1.5.1 to 1.11.1. Inspection of the two Media3 renderer factories shows that 1.5.1 auto-loads the bundled `Libgav1VideoRenderer`, while 1.11.1 looks for `Libdav1dVideoRenderer` instead. The registration-only probe bridge crashes on real AV1 playback because of a JNI field-type mismatch. Matching dav1d and FFmpeg replacements were rebuilt and smoke-tested on x86_64 only; integrating them safely requires resolving the app API changes and validating each shipped ABI and device playback.
- GeckoView requires Android 26 or newer; API 24–25 need a safe tested fallback.
- An API 28 software emulator was used for limited runtime checks in an earlier environment; no physical Android device is connected for hardware, sign-in or playback validation.
- The experimental Gecko components are outside the app source set and the current shipping browser remains WebView.
- The test probes do not prove decoder, Google sign-in, HLS or private-browsing behavior on devices; a safe browser cutover requires those checks and feature parity.
