# Gecko browser migration — experimental foundation

Status: **not integrated and not a browser replacement**. An isolated Gecko HTML smoke test and matching decoder playback tests ran on an API 28 x86_64 emulator; the browser itself was not tested in the app. The session class compiled in an isolated Android project against the classes from GeckoView 159.0.20261102144711 (API 26, Java 17); this does not validate packaging or behavior. The shipping WebView browser is untouched. `GeckoTabEngine.kt` is outside the app source set and is not included in the APK.

The inspected stable Mozilla artifact `org.mozilla.geckoview:geckoview:159.0.20261102144711` is distributed from Mozilla's Maven repository. The matching ARM64 AAR manifest declares minimum Android API 26; Hi Player currently supports API 24. A production migration needs an explicit API 24–25 compatibility strategy and ABI coverage. Do not silently raise minSdk or ship ARM64-only. Mozilla's setup requires Java 17; Hi Player currently targets Java 11.

## Preserved existing behavior required before cutover

- [ ] Tabs (including private isolation), lifecycle, back/forward, restore and recent-tab selection; do not persist private data or credential-bearing URLs.
- [ ] Address input/search provider, home shortcuts, bookmarks, browsing history, reload, progress, expired-link and offline error handling.
- [ ] Site permissions, file selection, downloads, upload, sharing, pop-ups, external intents and full-screen video.
- [ ] Existing per-site and browser settings, desktop mode, dark mode, text zoom, clear-data controls, private mode and accessibility.
- [ ] Video/media detection, user-initiated playback handoff, background behavior and PiP, without fabricating support.
- [ ] Browser content filtering and script behavior where compatible and lawful; do not advertise unsupported behavior as working or bypass provider security.
- [ ] Account and website sign-in: preserve secure browser authentication and separate website sessions. GeckoView alone does not guarantee Google account selection or OAuth permission.
- [ ] Source and license notices for GeckoView and its redistributed components.
- [ ] Dependency/ABI/minSdk/build size and update process; migration and rollback for saved bookmarks and settings.
- [ ] Compile, automated tests, manual device validation and Google/site sign-in verification before changing the default browser. Keep the existing browser until parity is demonstrated.

The experimental session class covers basic session lifecycle, a private-mode flag, page loading/progress, navigation state, safe error categories and desktop settings. Location changes update the in-memory address, and a loading state starts as soon as a URL is requested. The in-memory tab registry and session controller connect tab selection to per-tab sessions, and can initialize regular-tab identity/opener metadata while discarding private entries; actual settings-store wiring and URL/history restoration remain unimplemented; ten registry tests, eight controller tests, seven loading-state tests, five routing-policy tests and four appearance-policy tests passed in an isolated Android build; the controller tests use fake sessions and routing tests use stub policy inputs. Apart from the isolated local HTML smoke test, private-session isolation and device behavior have not been tested. The controller is not wired into the app UI and does not yet supply secure OAuth, downloads, popups, persistence, filtering or video integration.

The experimental navigation delegate can request secure sign-in or external-site handling through injected callbacks. It does not perform authentication or launch a browser itself. The future UI integration must use the existing `SecureWebSessions` policy, present the sign-in prompt, and never launch an external activity for a redirect without a user gesture. This has not been validated against real providers or a Gecko runtime.

An experimental runtime-wide appearance adapter maps dark mode and text zoom while accounting for the device font scale. Gecko text-size changes need a page reload and dark mode is a site preference, not proof that every page will be dark. The adapter is not connected to the existing settings store or UI, and it has not been validated on a device.

## Dependency compatibility probe

An isolated API 26, Java 17 test project with Hi Player's AGP 9.1.1 / Kotlin 2.2 toolchain was tested against real Maven artifacts (not just a local `classes.jar`). With GeckoView ARM64 159.0.20261102144711, Kotlin compilation failed because the resolved Kotlin stdlib 2.4.20 has metadata newer than the compiler supports. The 159 POM also requests Media3 1.11.1, potentially changing the app's existing Media3 dependencies. Do not add this dependency to the app without resolving and testing the full dependency graph.

The same isolated project's 34 unit tests passed with the real ARM64 GeckoView 150.0.20260511200624 artifact. Those tests use fake sessions and do not exercise a running Gecko engine or the existing browser. This older version is a compatibility probe, not a security or release recommendation; review current supported releases and security updates before choosing a production version. Neither result verifies API 24–25, other ABIs, app integration, native runtime, website sign-in, or the feature-parity checklist above.

A separate source-only app compilation probe copied these experimental classes into the app and temporarily added the 150 ARM64 dependency with Java 17. Kotlin compilation succeeded; manifest merging initially failed because both GeckoView and `geckoview-exoplayer2` require API 26. A second local probe forced both manifest overrides and merged successfully. This override is **not a compatibility fix**: Gecko must not be loaded on API 24–25, and those devices need a tested fallback. The probe was not committed to the app, does not exercise or replace the WebView UI, and does not establish runtime or media-player compatibility. No APK was produced.


An experimental Gecko file-upload prompt delegate now creates system document-picker intents for single, multiple and folder selection, normalizes accept hints and prevents late results from being delivered to a dismissed page. It is not wired to the production UI; host Activity Result integration, runtime/device tests, camera capture and full upload parity are still needed. The unit tests cover the single-request gate and MIME normalization only.


Experimental popup-session support can reserve a background child tab that inherits its opener's private mode and supply a newly created, unopened session to Gecko. The host must enforce popup/security policy, connect the callback, select the child after Gecko opens it, handle cleanup, and test real websites; none is wired into the current browser. Controller unit tests use fake sessions and cannot validate Gecko's actual popup lifecycle.

Experimental download handling takes ownership of Gecko's response body (including authenticated responses) without copying cookies or refetching through a different client. It closes unused streams and includes a worker-side transfer primitive with cancellation, actual byte progress, and destination commit/abort hooks. The browser UI, download destination implementation, existing downloads list/service, background persistence, retry, filename selection and device validation are not yet connected. Tests use in-memory streams and fake destinations; they do not verify real Gecko networking or an Android download.

An experimental permission broker forwards website and Android permission requests to a host consent UI, denies unsupported capture types, and ignores duplicate or stale decisions. It is not connected to any app UI, Android runtime-permission launcher, or site settings, and has not been exercised with a real Gecko runtime. Its unit tests cover origin display sanitization and the single-request cancellation gate, not actual permission grants.

An experimental Compose `GeckoBrowserSurface` attaches the selected session to a GeckoView and ignores late release of an old view. A file-picker launcher now connects the upload delegate to Android's Activity Result registry with unique request keys; Robolectric tests cover multiple URIs, cancellation, and stale result isolation. These components remain outside the app source set and are not yet wired to the existing browser screen, lifecycle, or settings. No device or live-website validation has been done.

An app-source patch in `patches/shared-address-input.patch` extracts address/search normalization for reuse between engines without changing its behavior. All three targeted tests passed in a local working copy; the patch has not been applied to the remote branch because the browser screen exceeds the GitHub connection request limit. The extraction does not change the active browser engine or complete the migration.

## GeckoView 159 app-toolchain probe (not a production configuration)

In a separate copy of the app, GeckoView ARM64 `159.0.20261102144711` compiled after using Kotlin/Compose compiler 2.4.20, KSP 2.3.13, Java 17, Android compile SDK 37.2, and **temporarily setting minSdk 26 for the probe only**. The app's target SDK remained 36. These settings are recorded in `patches/gecko159-build-probe.patch`; it is not applied to the app in this branch. Two preexisting unit tests required explicit `Array<Any>` element types under Kotlin 2.4 and are adjusted in the branch. The experimental Gecko files and tests were copied into the probe app; 76 targeted tests passed (55 Gecko, 21 existing browser/media/auth tests). Most Gecko tests use fakes or Robolectric; none exercises a real Gecko runtime.

Important limitations: the probe was ARM64-only and did not produce an APK. AGP 9.1.1 warned that it had been tested only through compile SDK 37.0, not 37.2. The dependency graph resolved the app's Media3 1.5.1 to 1.11.1, which could affect the existing player and bundled decoder AARs; compilation and selected unit tests do not establish playback compatibility. API 24–25 compatibility, all-ABI packaging, real-device playback, provider authentication, and browser feature parity remain unverified. Do not apply the minSdk increase to a shipping build without a tested fallback or an explicit compatibility decision. The shipping browser still uses WebView.

A new API 29+ MediaStore download destination creates a pending Downloads entry only when a confirmed stream transfer starts, publishes it on successful completion, and attempts to remove incomplete entries on failure or cancellation. It uses the existing filename/MIME sanitizer and does not transfer cookies between engines. Ten targeted transfer and destination tests passed in the Gecko 159 app probe; the new destination tests use a fake storage backend, not a real device or MediaStore provider. This destination is not connected to the browser UI or downloads queue. At that stage Android 26–28 and custom folders needed a separate destination; see the later experimental tree destination below. Crash recovery, background persistence, retry, and actual provider behavior remain unimplemented or unvalidated.

## Media3 software AV1 registration probe

`LegacyGav1RenderersFactory.kt` and `tests/LegacyGav1RegistrationTest.kt` are experimental files outside the app source set. In the isolated GeckoView 159 / Media3 1.11.1 probe, the normal factory did not register the bundled 1.5.1 Libgav1 renderer; the app regression test detects this. The experimental factory adds that renderer while preserving hardware-first ordering for extension mode ON. Three Robolectric tests checked extension ON, OFF and PREFER registration/order. These tests do not decode video, load native libraries on a device, verify binary compatibility, or establish FFmpeg/HLS compatibility. Do not use this bridge in a release until those are validated; the shipping player is unchanged.

**Runtime finding:** The registration-only legacy Libgav1 bridge is unsafe. An API 28 x86_64 emulator crashed during actual AV1 decoder initialization: the bundled JNI looks up `VideoDecoderOutputBuffer.decoderPrivate` as `int`, but Media3 1.11 uses `long`. Do not ship the bridge. The same isolated emulator probe successfully loaded a local HTML page in GeckoView 159; it did not test the existing browser, provider sign-in or device playback. Reproduction is in `runtime-probe/README.md`.


## Matching Media3 native decoders (isolated)

`aligned-decoders/` contains pinned-source build configuration for Media3 1.11.1 dav1d and FFmpeg extensions, and `aligned-probe/` exercises them with GeckoView 159. Both extensions were built for arm64-v8a, armeabi-v7a, x86 and x86_64; the existing FFmpeg decoder selection was retained. A software API 28 x86_64 emulator passed local HTML loading, native dav1d AV1 playback and native FFmpeg E-AC-3 playback. The remaining ABIs were not runtime-tested. The clean-fetch script also completed end to end and verified four-ABI AAR packaging. An isolated runtime test APK compiled with the new output, but emulator installation failed while Android system_server restarted under watchdog; this output has not yet been runtime-tested.

The probe's Gradle dependency graph resolves all Media3 modules to 1.11.1. These replacements are not installed in the Hi Player app. A full-app upgrade still needs proper fixes for removed Media3 MP4 and GL APIs, tested compatibility, licensing review and end-to-end validation. A diagnostic-only copy passed 31 selected tests after temporary alterations, but is not a production integration. API 24–25 fallback and browser parity remain open.


## Navigation and player compatibility candidates

The experimental tab controller forwards reload, stop, back, forward and desktop-mode commands to its selected Gecko session. Twenty-three tab registry/controller tests passed in an isolated GeckoView 159/Media3 1.11.1 app copy. These use fake sessions and do not validate the actual browser UI, sites or device behavior.

`patches/media3-111-player-candidate.patch` is **not applied to the app on this branch**. It replaces a removed GL base class in an effect that the current player does not install, implements the shader, and replaces the inverse MP4 flags with the explicitly enabled old bits. It compiled against 1.5.1 and 1.11.1, and a flag-value regression test passed in each. MP4 flag bit 6 has different semantics in the two Media3 versions, so equal numeric values do not establish equivalent behavior. The shader has not been rendered and MP4 playback, HDR and seeking have not been device-tested. Do not apply for release until reviewed and validated.

## Experimental saved-tab adapter

`GeckoTabPersistence.kt` converts existing `OrbitSavedTab` records to registry tabs and display state and converts normal Gecko tabs back for the existing store. It excludes private tabs, invalid opener IDs, non-web URLs and known credential-bearing URL parameters. Detection is conservative but not exhaustive; the host still needs to handle other sensitive URLs safely. The controller accepts restored state and loads URLs with the saved desktop mode. Twenty-five fake-session/registry/persistence unit tests and one Robolectric test of the existing preference store passed in a temporary app copy. This code is outside the production source set, is not wired to the browser UI, and has not been tested with a real Gecko runtime. It restores URLs, not full back/forward history.

## Experimental browser chrome and coordinator

`GeckoBrowserCoordinator` connects tab metadata, session snapshots and the existing tab store while filtering private tabs and rejecting callbacks from replaced sessions. `GeckoBrowserHost` builds actual `GeckoManagedTab` sessions using host-supplied navigation, permission, picker, popup, download and close callbacks. `GeckoBrowserShell` connects the existing Hi Browser home, toolbar and tab switcher to the coordinator, including shortcuts, navigation, reload/stop, progress, basic errors, and fresh-session Home. The host must own the coordinator across UI reattachments and close it at the appropriate lifecycle boundary.

This is an **experimental partial shell** outside the app source set: no app route selects it. Menu/settings and voice actions are supplied by a future host, and filtering is not connected (the shell does not advertise it as active). Downloads, popup handling, permission UI, sign-in routing, video/fullscreen/PiP, device lifecycle and many existing features are not wired. In a temporary app copy with GeckoView 159, the source compiled and 34 focused tests passed; coordinator tests use fake sessions, and the one Compose shell test uses a fake session and page surface. These tests do not exercise real GeckoView UI or prove browser parity. No test APK or release was produced.


## Experimental website permission UI

`GeckoPermissionUi` supplies origin-scoped consent, an explicit device selector for camera and microphone, and a `GeckoPermissionDialog` that can be mounted by `GeckoBrowserShell`. `GeckoRuntimePermissionLauncher` checks the actual app manifest and OS grants, uses unique Activity Result keys, and denies outstanding requests on close. Create the launcher with the host Activity Result registry, then create the UI, pass it to `GeckoPermissionBroker`, and pass that broker to the coordinator and the UI to the shell; close the broker and UI when the owner is disposed. Denial is the default for unsupported content types.

These components are not routed into the production app. The current manifest lacks camera, microphone and location permissions, so those requests are denied; adding and disclosing permissions, actual UI integration, site settings, notification handling, and device/live-site testing remain necessary. Do not advertise those capabilities as functional based on this prototype.


## Experimental history integration

The coordinator records successfully completed non-private page visits through the existing `OrbitBrowserStore` on a background executor, and the shell displays existing local history suggestions when the regular-tab address field is focused. It filters known credential-bearing URLs, ignores failures and cancellations, and updates the entry when a completed page reports a later title. In an isolated Gecko 159 app probe, 25 targeted coordinator, persistence, loading, store and Compose shell tests passed. These use fakes/Robolectric, not a live Gecko page. Single-page navigation, redirect handling and coordination of queued writes with history clearing need further work and device testing. URL filtering is not exhaustive, and this code is not included in or routed from the shipping app.


## Experimental popup ownership

The coordinator can reserve an unopened child session, inheriting the opener's private mode, and selects it only when it begins loading and its opener is still selected. The engine gates popup requests using the popup preference and a single-use, five-second URI ticket. Nineteen focused fake-session, gate and Compose tests passed in a temporary Gecko 159 / Media3 1.11.1 app copy.

The isolated GeckoView 159 instrumentation test in `aligned-probe` passed on an API 28 x86_64 software emulator for one user-gesture `about:blank` popup: a matching new-window request preceded `onNewSession`. It uses a touch retry for slow headless emulators. This checks one callback ordering case only, not the real host or website sign-in. The host now closes popup-owned sessions when Gecko requests it and discards reservations still unopened after 30 seconds, with a session-identity check; real device and lifecycle validation remain required. Other popup forms, cancellation and sign-in remain unverified. The code is experimental, outside the production app, and does not establish feature parity.


## Experimental menu, library and history clearing

`GeckoBrowserChrome` wires the existing menu and library to normal/private tabs, back/forward, reload, desktop mode, bookmarks and history; settings, downloads and exit are host callbacks. It does not provide all the existing WebView browser's actions, and is not connected to the app route. Clear and delete operations are serialized with queued visit writes; existing page callbacks are suppressed until the next load, and history suggestions are refreshed when the library changes. URLs with known credential-bearing parameters are not saved as bookmarks. Thirty-six focused fake-session, Robolectric and Compose tests passed in an isolated app copy. They do not validate a real device, all sensitive URL formats, full browser behavior, or the main app build.


## Experimental download lifecycle and services

`GeckoBrowserServices` owns the coordinator, Activity Result picker, permission broker and in-memory download controller and closes them on disposal. The experimental Chrome can display download consent and transfer state. On Android 29+ the confirmed Gecko response stream is written directly into a new pending MediaStore Downloads entry; no cross-engine cookie or header copying occurs. The stream cannot resume and is cancelled when the owner closes. A second simultaneous offer and unsuccessful HTTP responses are declined; the user receives a notice. Twenty-four focused tests covering the controller, fake destination, picker, permission UI and Compose UI passed in the isolated app probe. These do not test a real Gecko download or Android MediaStore.

This remains outside the production source set and is **not feature complete**. An experimental custom-folder destination has since been added (below); active-transfer integration with the existing download queue, background persistence, pause/resume/retry, process death cleanup and actual provider/device testing remain open. Do not switch the shipping route or present this as the completed browser.


## Experimental selected-folder downloads

`GeckoDocumentTreeDestination` streams into a newly created document in a user-selected Storage Access Framework tree. It appends a random suffix to reduce name collisions and attempts to remove its own incomplete document on failure or cancellation. The experimental dialog asks for a folder on Android 26–28 and offers an optional folder on newer versions. Twenty-three focused tests passed in the isolated probe; the storage tests use a fake backend, and the API 28 UI test verifies only that selection is required. Real picker/provider behavior, persisted access, process death cleanup and active-transfer queue integration have not been tested or completed. Failed cleanup or process death can leave a partial file.


## Experimental completed-download history

Completed regular-tab downloads can be added to the existing downloads history using their content URI; source URLs, user agents, cookies and referrers are not copied into those records. The saved journal is checked before reporting that the record was added. If recording fails the UI distinguishes a saved file from a missing history entry. Private-tab files remain saved on the device but are not recorded in browser download history. A selected folder is given a persistent grant only for regular downloads; private transfers do not request one. Existing history UI hides the empty copy-link action for these records.

The original Media3 1.5.1 app configuration passed 24 selected download model, resolver, UI and journal tests; the isolated Gecko/Media3 1.11.1 probe passed 34 focused tests. No actual provider, app restart, private-tab runtime, foreground service or physical device was tested. The experimental stream transfer is still owner-bound, cannot pause/resume, and active transfers are not in the existing queue; process-death cleanup and full browser integration remain outstanding.

## Experimental clear browsing data

The experimental menu can request clearing selected history, site cookies/local storage/auth sessions, and cached images/files. It requests history purge for every open tab, clears the existing ordered history store and saved site icons, and removes the app's browser-routing preferences. Device-browser cookies are not touched. The UI reports partial failures; Gecko's session purge offers no completion callback, so the UI does not claim that purge is confirmed. This is not connected to the shipping browser and does not verify all possible site-storage categories. Thirty-eight selected Gecko tests passed in the isolated Gecko 159 / Media3 1.11.1 probe, using fake sessions/Robolectric; real Gecko storage, interruption, and device behavior remain untested.

## Experimental appearance and pop-up settings

`GeckoBrowserSettings` restores the existing saved dark-webpage, webpage-text-size and popup preferences. The Gecko owner applies appearance before creating sessions, and its shell reapplies on system font-scale changes. A compact experimental menu panel persists those settings and warns that text-size changes require a page reload, which is not forced. Pop-up enforcement reads the same existing preference. Other settings and the original host Settings action are not migrated by this panel. Twenty-three focused fake-session, Robolectric and Compose tests passed in a restored isolated Gecko 159 / Media3 1.11.1 probe; no real Gecko runtime, hardware, actual sites, full settings or release app were validated.

## Experimental find in page

The experimental menu opens the existing find bar for the current Gecko tab. The session's native finder handles search/highlighting and previous/next; changing tabs or pages closes the bar, clears highlights and invalidates late results. Unknown counts are not fabricated. Thirty-six selected fake-session, Robolectric and Compose tests passed in the isolated probe; native Gecko matching, real sites, selection and device lifecycle remain unverified. The production browser is unchanged.
