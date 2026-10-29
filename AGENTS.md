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
