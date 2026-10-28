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

## Real project OAuth configuration
- Firebase project: `hi-player-979d7` (Hi Player), on the free Spark plan. No billing account, Analytics, Gemini, or optional developer-program enrollment was enabled during setup.
- Google is enabled in Firebase Authentication. Public-facing name: Hi Player; support email: hi033029@gmail.com.
- Android registration: `com.aistudio.hiplayer.vdmk`. The existing debug/APK signing key's SHA-1 and SHA-256 are registered:
  - SHA-1: `B2:33:2F:25:44:BC:1E:68:99:12:7B:79:C2:3C:D6:44:1B:AF:33:C8`
  - SHA-256: `D5:47:6A:B2:9C:C7:41:FC:C4:9D:65:A7:C3:9A:2F:B7:06:2D:FE:80:C1:A6:8A:33:6E:6F:81:E7:91:5D:5F:68`
- `app/google-services.json` is now the real Firebase-generated Android configuration, replacing the placeholder project. It includes the package/certificate-bound Android OAuth client and same-project Web OAuth client. No OAuth client secret is included.
- The Google Services plugin uses the type-3 Web client to generate `default_web_client_id`; an environment/property override is unnecessary for this project. If an override is supplied, it must be the Web client from this same project.
- Release/Play app-signing keys require their own real fingerprints before those variants can sign in. The currently registered certificate matches this repository's existing debug APK signing key only.

## Remaining console and runtime validation
- Google Cloud Auth Platform's audience page is gated by a separate first-use Google Cloud terms/country dialog. Those terms have NOT been accepted; the earlier approval covered Firebase terms only. Consent-screen publishing status and test-user restrictions therefore remain unverified. Do not claim public production OAuth readiness until this is checked.
- No privacy-policy URL, verified domain, billing enrollment, or broad Gmail/Drive scopes were invented or added.
- Configuration was checked as JSON for project/package, Android certificate, Web client presence, and absence of a client secret. These are static checks, not an Android sign-in test.
- Native account sessions and browser website cookies remain separate. App sign-in cannot automatically sign every embedded website into Google.
- Missing or wrong configuration remains an explicit setup error; it never invents a signed-in user.

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
