# Hi Player working rules

## Build approval (required)
- Ask Khan before starting an APK build. Do not assume a feature request approves a build.
- While Khan is adding features, make source-only changes and leave the APK version unchanged.
- Do not run Gradle, compile, lint, package, release, or unit/instrumentation test tasks without explicit build/validation approval.
- Pushing/merging main triggers the Build APK workflow. Do not push or merge main, dispatch that workflow, or publish an APK without approval.
- Source-only feature branches and code review are allowed; never invoke the APK workflow as a source-sync side effect.
- Once a new build is approved, increment versionCode and the versionName patch, preserve the committed signing key, then build/test and report results honestly.

## Authentication
- Use Google's official Credential Manager flow and Firebase Authentication validation, not a fake account picker or decoded JWT as proof of authentication.
- Never collect Google passwords or copy tokens/cookies between app authentication and WebView/browser sessions.
- Google website authentication uses a supported Custom Tab. Website sessions remain separate from the Hi Player account.
- Missing OAuth configuration must be shown as setup-required; never fabricate OAuth client identifiers or claim live sign-in is working before configured and tested.

## Themes
- Prefer subtle theme-aware separators, not high-contrast frames around every container. Never outline each bottom-nav item. Player overlays must not inherit the light library theme's outline.
- Do not put container frames over the video-rendering surface or change media playback behavior as part of styling.

## Google Drive
- Request read-only Drive authorization only on user action under the signed-in Google identity. Verify Drive API owner email and clear local credentials/lists on sign-out or account changes.
- No separate Android account chooser, Firebase ID token for Drive, cookie copying, credential URLs, generic stream-header registration, or bearer forwarding through redirects.
- OAuth access tokens stay in memory. drive.readonly is restricted; public distribution requires consent configuration and Google's review requirements. Never claim verification without evidence.

## UX behavior (required)
- Acknowledge taps immediately: publish pending/busy state before I/O, keep expensive work off the main thread, prevent duplicate submissions, and show success/failure rather than leaving users guessing. Pending is not success.
- Use static, layout-matched skeletons for known lists/grids; preserve cached content during refresh where appropriate. Unknown web layouts and indeterminate operations may use truthful progress indicators. Do not invent percentages, progress, data, or completion.
- Keep onboarding primary actions at the same bottom position, size and margins with safe system insets. Content scrolls above them. Provide visible Back/Skip/Not now as appropriate; never immediately re-prompt after a user skips permission setup.
- Keep cancellation and dismissal discoverable. Cancel pending setup jobs and check cancellation before saving credentials; do not pretend cancellation reverses a remote operation already completed. Preserve confirmation for destructive actions.
- Never render backend bodies, HTTP codes, stack traces or raw exception messages as app error copy. Show a short, honest description with a recovery action. Log only safe event/type information; do not put secrets, credential-bearing URLs or personal file paths in diagnostics. Never hide a security failure by bypassing TLS/authentication.
- No manipulative opt-outs, forced consent, guilt copy, or hard-to-cancel subscriptions. Apply this checklist to new UI and verify loading/error/slow/double-tap/cancel states during the next approved build/device test. Source review alone is not runtime validation.
