# Gecko browser migration — experimental foundation

Status: **not integrated, not compiled, not tested, not a browser replacement**. The shipping WebView browser is untouched. `GeckoTabEngine.kt` is outside the app source set and is not included in the APK.

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

The experimental class covers session lifecycle, private-mode flag, basic page loading/progress, navigation state and desktop settings only. It does not yet supply tabs UI, secure OAuth, downloads, popup handling, persistence, filtering or video integration.
