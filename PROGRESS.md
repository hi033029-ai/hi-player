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
- The shared address-input extraction was tested in a separate copy but has not been applied to this branch.

## Blockers and risks
- GeckoView 159 resolves Media3 from 1.5.1 to 1.11.1. Inspection of the two Media3 renderer factories shows that 1.5.1 auto-loads the bundled `Libgav1VideoRenderer`, while 1.11.1 looks for `Libdav1dVideoRenderer` instead. Upgrading without replacing/rebuilding and testing the AV1 extension would regress the existing software AV1 fallback. FFmpeg binary compatibility also needs runtime validation.
- GeckoView requires Android 26 or newer; API 24–25 need a safe tested fallback.
- No connected Android device is currently available for real runtime, sign-in, or playback checks.
- The experimental Gecko components are outside the app source set and the current shipping browser remains WebView.
- The test probes do not prove decoder, Google sign-in, HLS or private-browsing behavior on devices; a safe browser cutover requires those checks and feature parity.
