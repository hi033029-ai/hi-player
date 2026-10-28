# Account-linked Drive and UI polish — source only

## Status
Based on released v1.0.66 (67). No version bump, Gradle invocation, compilation, lint, tests, APK generation, main merge, or APK workflow dispatch for this change. Added tests are unrun. The existing 67-test passing result belongs to the previous release, not this change.

## UI
- Theme borders are quiet neutral separators rather than high-contrast frames. Bottom navigation keeps only its outer pill, with no individual outlined tab cells or outlined active indicator.
- Player sheets no longer inherit a light-theme library outline. Subtitle content uses the available sheet width up to 560dp instead of a 78%-width/320dp nested column. Long fixed-height labels are replaced by short actions with at least 56dp height and scrolling.
- Google is the default search engine; a user's subsequent engine choice persists. This is not a change to Android's default-browser setting.
- Provider logos have no white background plate, selection fill, or circular outline. The edge-connected exterior white background in the DuckDuckGo bitmap was also removed, preserving its enclosed artwork.

## Google account and Drive
- Google/YouTube/Gmail web shortcut buttons were removed from the account card: native Google login cannot sign a separate browser cookie jar in.
- Removed the separate Android-account/SAF Drive setup card and generic Drive source choice. Drive is reached from Settings → Google account → My Drive, using the existing verified Google identity.
- Official Google AuthorizationClient requests read-only Drive access on user action, explicitly bound to that identity. Drive API owner email is checked before listing. Native sign-in alone does not grant access to Drive.
- Native folder/media listing, filename search, pagination, refresh, disconnect, permission cancellation/errors, file-owner download restrictions, and video/audio playback path are implemented in source.
- OAuth access tokens stay in memory. No Firebase ID token is reused as a Drive token; no bearer in history, URLs, or the generic remote header registry. Playback supports Range seeks and silent reauthorization; a required new consent returns a reconnect message rather than opening a surprise consent UI.
- Drive requests use only the exact HTTPS Drive API. CDN redirects are HTTPS Google-host allowlisted and receive no bearer header. API/redirect response details containing credentials are not surfaced or logged. Local token/list state clears on sign-out or account changes.
- Legacy SAF Drive data types remain only for compatibility; new account-integrated browsing does not treat an old folder grant as proof of Google account ownership.

## Project setup / release gate
Google Drive API is enabled in the existing `hi-player-979d7` project. No billing or unrelated APIs enabled.

Browsing/downloading the user's existing Drive media requires `https://www.googleapis.com/auth/drive.readonly`, which Google classifies as restricted. Google's console explicitly requires verification. The candidate scope declaration was discarded without saving; production consent scopes remain unchanged. No verification submission or fake policy URL/demo was made.

`GOOGLE_DRIVE_ENABLED` defaults to false. Only explicitly set `HI_PLAYER_DRIVE_ENABLED=true` for an approved staging/test consent flow or after required production approval. The account screen explains pending setup; disabled setup must never be shown as a connected Drive library.

Before public activation: supply a real app homepage and privacy policy, confirm domain ownership/branding, configure/review the restricted scope, and provide Google's required demo of actual consent and data use. Security assessment requirements depend on the final deployment and Google's policy; do not claim exemption without review. Keep write/delete scopes out.

## Validation still required after build approval
- Compile new SDK/data-source/UI integration; run Drive origin/identifier/query tests, border regression tests, portrait/landscape subtitle tests, and prior media/browser tests.
- Physical Play-enabled device: Google account selection, Drive consent grant/cancel, account mismatch/change, revoked/expired permission, offline/API disabled errors, sign-out, folder navigation, large paginated libraries, owner-restricted media, MP4/MKV/audio playback and seeking.
- Test redirects without forwarding bearer tokens, invalid Content-Range, ignored Range, restart with no memory token, and post-sign-out requests.
- Portrait/landscape, large fonts, light/dark/custom themes: subtitle labels readable and no repeated outlined tabs.

## References
- https://developer.android.com/identity/authorization
- https://developers.google.com/drive/api/guides/about-auth
- https://developers.google.com/drive/api/reference/rest/v3/files/list
- https://developers.google.com/drive/api/guides/manage-downloads
