# Google account and theme outlines — source-only, awaiting build approval

## Status
- Official Google Credential Manager chooser/button flow exchanges the Google ID token with Firebase Authentication. Only a backend-validated Firebase Google user is displayed as signed in.
- Name, email, and profile photo are shown in Settings; the shared main header has an account/avatar shortcut.
- Firebase owns session persistence/refresh. No password UI and no raw OAuth/refresh token storage, logs, or cookie injection were added.
- Sign out clears Firebase state and Credential Manager state. It does not sign websites out.
- Native sign-in requests identity only; no Gmail inbox or broad Google Drive API access. Existing Drive document-tree grants are separate and relabeled accordingly.
- Secure Google/YouTube/Gmail website entry uses supported, non-ephemeral Custom Tabs. Google authentication reached from WebView prompts to reopen the original website in the secure browser and restart its sign-in there. Do not transfer an OAuth state/callback from the WebView and pretend its cookies match.
- Explicit warning before leaving private browsing for a regular signed-in secure browser. Embedded browser ad filtering/media controls do not apply inside Custom Tabs.
- Normal WebView cookies are accepted, third-party blocking stays enabled, and cookies flush when the app stops; server expiration, sign-out, clearing data, and revocation still apply.
- Theme borders no longer use transparent built-in colors. A shared contrast-aware outline covers Material cards and custom rounded containers across library, folders, music, file manager, settings, browser, and player-control containers. Video-rendering surfaces are not framed.

## Required project configuration (not yet supplied)
The checked-in google-services.json contains no OAuth client entries. This source change cannot manufacture a working Google project configuration.

1. Open the existing Firebase project and enable Authentication → Sign-in method → Google. Configure the project's real support email and consent screen as required.
2. Register the Android app `com.aistudio.hiplayer.vdmk` and its actual signing SHA-1/SHA-256. Debug APKs use the repo's committed debug.keystore; release/Play app-signing keys need their own fingerprints.
3. Create/confirm the Android OAuth client for that package/certificate and a Web application client in the same Google Cloud/Firebase project.
4. Download the updated google-services.json into app/google-services.json. The Google Services plugin should generate default_web_client_id from its Web OAuth client.
5. Alternatively supply the real Web client identifier through the `HI_PLAYER_GOOGLE_WEB_CLIENT_ID` Gradle property or environment variable. This is a public identifier, **not** a client secret; do not ship a Web client secret in Android. The ID must belong to the same configured Firebase project.
6. Add test users if the consent screen is in testing. Confirm production branding/privacy-policy requirements before release.
7. Missing configuration leaves native sign-in unavailable with an explicit setup message; it never invents a signed-in user.

## Build hold
No Gradle, compile, lint, package, or test task was run for these changes. No APK was generated, version bumped, main merge made, or APK workflow dispatched. Authentication and theme tests have been added but are unrun. Preserve this hold until Khan approves validation/build.

## Validation after approval
- Resolve new SDK dependencies; compile and run authentication-policy, secure-session, theme contrast, and UI regression tests.
- Physical Google Play-enabled device: successful chooser + Firebase exchange; cancellation; offline; wrong OAuth configuration; process restart; sign-out/account switching; token revocation handling.
- Check account avatar/title layout on narrow screens and large fonts.
- Google/YouTube/Gmail sign-in in a supported secure browser persists there; confirm embedded WebView remains a separate cookie jar and original tabs are preserved.
- Private-mode transition warns; no installed Custom Tabs browser gives an actionable error, not an insecure fallback.
- All three built-in themes and custom transparent/low-contrast themes: visible card outlines with correct rounded corners; no frame overlay over video content.

## Official references
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs
