# Gecko browser migration — experimental foundation

Status: **not integrated, not runtime-tested, not a browser replacement**. The session class compiled in an isolated Android project against the classes from GeckoView 159.0.20261102144711 (API 26, Java 17); this does not validate packaging or behavior. The shipping WebView browser is untouched. `GeckoTabEngine.kt` is outside the app source set and is not included in the APK.

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

The experimental session class covers basic session lifecycle, a private-mode flag, page loading/progress, navigation state, safe error categories and desktop settings. Location changes update the in-memory address, and a loading state starts as soon as a URL is requested. The in-memory tab registry and session controller connect tab selection to per-tab sessions, and can initialize regular-tab identity/opener metadata while discarding private entries; actual settings-store wiring and URL/history restoration remain unimplemented; ten registry tests, eight controller tests, seven loading-state tests, five routing-policy tests and four appearance-policy tests passed in an isolated Android build; the controller tests use fake sessions and routing tests use stub policy inputs. Actual Gecko runtime, private-session isolation and device behavior have not been tested. The controller is not wired into the app UI and does not yet supply secure OAuth, downloads, popups, persistence, filtering or video integration.

The experimental navigation delegate can request secure sign-in or external-site handling through injected callbacks. It does not perform authentication or launch a browser itself. The future UI integration must use the existing `SecureWebSessions` policy, present the sign-in prompt, and never launch an external activity for a redirect without a user gesture. This has not been validated against real providers or a Gecko runtime.

An experimental runtime-wide appearance adapter maps dark mode and text zoom while accounting for the device font scale. Gecko text-size changes need a page reload and dark mode is a site preference, not proof that every page will be dark. The adapter is not connected to the existing settings store or UI, and it has not been validated on a device.
