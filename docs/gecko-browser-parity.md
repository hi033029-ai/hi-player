# WebView and Gecko browser parity audit

**Scope:** source-level comparison of `HiBrowserScreen`/shared browser UI with the optional Gecko integration under `experiments/gecko-browser`. This is an implementation audit, not a statement that the latest working-tree edits have compiled or passed device tests.

## Parity added or corrected in this pass

- Website Google authentication is routed through the project's consented Custom Tab flow. Gecko no longer creates an embedded Google-login tab or accepts website cookies as proof of app sign-in. The Hi Player account and website sessions remain separate.
- The browser menu now offers QR scanning through the same Google Play Services scanner used by WebView.
- Page tools now offer page translation and a launcher-pinned shortcut, using the shared WebView helpers. These actions are disabled for credential-bearing or otherwise unsafe URLs.
- Gecko settings now expose the shared preferences for ad/tracker blocking, page-transition skipping, YouTube ad skipping where permitted, and website-video detection. Blocking/helper changes show an explicit reload action; video-detection preference changes are read by the feature bridge.
- System Back follows the shared policy: page history, then the browser home page, then exit. It no longer closes the current tab merely because other tabs exist.
- Explicit Stop immediately clears Gecko's loading state and a Gecko cancellation callback is kept from being presented as a failed page load.
- The existing “Open in secure browser” action is available only for a valid HTTPS origin and uses the same consent and separate-session route.

## Behaviors already shared or substantially matched

| Area | Current Gecko behavior compared with WebView |
|---|---|
| Browser start page and chrome | Uses shared address bar, tab/menu controls, saved shortcuts/bookmarks and browser library UI. |
| Navigation | Per-tab Gecko sessions support load, back/forward, reload, stop, desktop mode, tab selection, and regular-tab restore with URL sanitization. Private tab data is not persisted. |
| Search and bookmarks | Shared store and provider UI; bookmarks/history are managed through the same library components. |
| Appearance | Dark-page preference, system font-scale handling, and webpage text zoom are applied through Gecko settings. Site support for dark mode and text zoom remains dependent on the page. |
| Privacy and page tools | Gecko uses the shared rule engine for request blocking and cosmetic rules, with page-level pause, page skipper, YouTube helper, reader, share, and video handoff controls where the Gecko bridge supports them. |
| Pop-ups and permissions | Gecko has destination-aware popup policy and host permission/file-picker brokers rather than blindly forwarding every request. |
| Downloads | Gecko asks before taking ownership of the response stream, shows a real filename, bytes, sampled rate, saving/completion/error state, allows cancellation, and can write to Downloads or a user-selected folder. |
| Secure browsing | Uses the existing HTTPS-origin routing confirmation and Custom Tabs provider; no WebView/Gecko cookie copying is performed. |

## Remaining gaps before calling the browsers equivalent

1. **Downloads are not feature-equivalent.** Gecko currently owns one response offer/transfer at a time and the transfer stops if the browser closes; it cannot resume. The WebView download manager has a persistent list/queue, pause/retry, rename, share/open, details, and play-while-downloading actions. Gecko currently has consent, destination selection, progress/speed, cancel, result, and history recording, but not that full manager lifecycle.
2. **Filter management is incomplete in Gecko settings.** The shared preferences and rule engine are available, but the WebView's “check/update filters” and signed YouTube-blocker rollback controls are not exposed in the Gecko settings panel. The current settings reload action is not a substitute for updating filter data.
3. **Advanced per-site controls are narrower.** Gecko has the current-site pause switch and available page rules, but not the full WebView advanced protection/rule-management screen and all of its site allow/block editing workflows.
4. **Video behavior is partial.** Gecko exposes website-video detection and the existing explicit handoff path. WebView's auto-play-in-player preference and every media candidate/recovery path have not been shown to behave identically in Gecko.
5. **Network/error recovery is not identical.** Gecko and WebView use different engine callbacks and error taxonomies. Expired-link classification, offline diagnostics, and retry/return actions need device validation and may differ by engine/site.
6. **A few host/platform options differ.** The default-browser role request and some WebView-specific controls are not currently represented by the Gecko host. GeckoView requires API 26+, so Android 24–25 necessarily remains on WebView.

## Security and behavior differences that should remain intentional

- Google website authentication must stay in a supported Custom Tab with explicit user confirmation. Do not copy Gecko/WebView cookies or OAuth tokens into the Hi Player account.
- Pop-up filtering may be stricter in Gecko because it uses Gecko's new-session and destination callbacks; a change should preserve tap intent and block known ad destinations.
- WebExtension filtering and WebView request interception are different engine APIs. Shared rules do not prove that every banner, sponsor segment, pop-under, or video ad is removed. Creator-sponsored video segments are not equivalent to network ads.
- WebView remains the API 24–25 compatibility engine; Gecko must not be loaded on unsupported Android releases.

## Validation status

Only source review and generated-source/static validation are in scope for this pass. Gradle compilation, unit/instrumentation tests, APK generation, clean installation, and live-site testing require the explicit build/validation approval specified in `AGENTS.md`. The newly added UI/model tests are regression coverage, not verified passing results until run in an approved build.
