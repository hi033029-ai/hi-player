# Personal storage connections — source-only

Status: **uncompiled, tests authored but not run, no OAuth registration or provider/device/server validation**. No APK, version bump, main push or release. Do not present OneDrive/Dropbox as ready to sign in until the developer setup below is complete. SMBJ dependency resolution and Android compatibility also need approved validation.

## Connections card

Settings → Connections now includes Google Drive, OneDrive, Dropbox, Network folders (SMB), Jellyfin, and WebDAV · Nextcloud / ownCloud. Existing Google Drive authorization and Jellyfin/WebDAV functionality remain separate. No provider is silently signed in from the Hi Player Google profile.

### OneDrive

Native/public-client authorization code + S256 PKCE in a Custom Tab, consumers authority (personal Microsoft accounts only). Delegated `Files.Read` reads the user's own files; `User.Read` verifies the returned account through Microsoft Graph before a connection is shown. No write permissions or client secret. Root/nested folder listing supports Graph next-page URLs restricted to the exact Graph origin and drive path.

**Developer setup required:** register a public/native Microsoft application for personal Microsoft accounts, add the exact supported custom redirect `com.aistudio.hiplayer.connections://oauth/onedrive` under the native/mobile registration, configure delegated Graph `Files.Read` and `User.Read`, and provide the real public Application/client ID as Gradle property or build environment `HI_PLAYER_ONEDRIVE_CLIENT_ID`. Do not register this as a confidential web app or embed a secret. If the registration does not accept this native redirect, do not work around its validation: adjust the app redirect + manifest to a provider-supported native redirect and verify it before enabling distribution.

### Dropbox

Native public-client code + S256 PKCE in a Custom Tab; read-only scopes `account_info.read files.metadata.read files.content.read`. Verify `/users/get_current_account` before showing connected. Browse via `/files/list_folder` and its continuation cursor. Read file content using a temporary download link resolved just before playback.

**Developer setup required:** create a scoped Dropbox app, select appropriate Full Dropbox read access if the intended UX is browsing the user's whole personal drive (App Folder exposes only that folder), enable only the three read scopes above, register exact redirect `com.aistudio.hiplayer.connections://oauth/dropbox`, and supply its public app key through `HI_PLAYER_DROPBOX_APP_KEY`. Complete provider testing/production approval as required. No Dropbox secret belongs in the APK.

Both cloud entries show setup-required and disable Connect when their identifier is absent. The current source has no real IDs configured. Redirect callbacks require the expected provider path and random single-use state; PKCE verifiers stay in process memory. Codes, verifiers, access tokens and signed content URLs never enter preferences, saved-state Bundles, diagnostics or generic stream-header registration. Provider account identity is API-verified, not inferred from an unverified JWT.

**Current session limits:** no refresh token/offline scope/persisted credential store. Reconnect after process restart or access-token expiry. A cancelled/failed reconnect keeps the previous session. Disconnect invalidates local playback access but does not revoke provider consent or sign its website out. No background sync, upload, delete, file editing or multi-account federation. Cloud tokens are never forwarded to signed content hosts or redirects.

### Network folders (SMB)

Manual NAS/computer hostname/IP, share name, username/password and optional domain. Uses pinned SMBJ 0.13.0 with signing required, DFS and SMB1 disabled. Default requires encrypted SMB3; user may explicitly turn off encryption for a trusted local/VPN network and an older SMB2/3 server. No guest/anonymous flow, server discovery, port configuration, WAN exposure recommendation or server setup automation.

Credentials stay in VM/process memory, not a URL or saved-state Bundle. Verification checks the chosen share before activation, cancellation checks precede activation, and replacement/disconnect wipes the active password character buffer. Password strings in UI drafts are cleared on explicit discard/success; no claim of guaranteed JVM memory erasure. A connected folder can browse subfolders (lazy directory enumeration capped at 1,001 entries; first 1,000 shown). File access uses FILE_OPEN and read-only data/attribute access. No create/rename/delete calls. Random-access reads support seeking without downloading whole media files. Session identity is checked during playback; no credentials in history. Blocking network operations can take until their socket/call timeout to unwind after Stop.

### Nextcloud / ownCloud

WebDAV setup has named provider choices and guidance to copy the actual DAV address from the server's Files settings. These choices label the connection; they do not fabricate a universal server path or claim provider-specific OAuth. Paste the actual folder endpoint, including server base path and user ID; use the provider's app password when required. One active WebDAV source remains supported, with existing encrypted saved profiles under Manage.

## Browsing and playback

New cloud/SMB screens have Back following visited folders, static file-list skeletons for first load, cached refresh, Stop/Retry, confirmed disconnect and paginated cloud lists (2,000 displayed entry cap). Up to 20 folder pages cached only while the browser session is open; cloud/SMB screens clear their caches on dismissal/account change. Supported direct audio/video formats open Hi Player. Unsupported files remain visible but disabled. No thumbnails, transcoding, search, offline downloads or watch-state sync in this pass.

Media history stores opaque `hiplayer-cloud` / `hiplayer-smb` session-bound URIs, never provider access tokens or signed download URLs. Such history entries require a new selection after disconnect/restart because their old session ID is intentionally invalid. Dedicated data sources handle cloud HTTPS range requests/redirects and SMB file offsets. Existing Google Drive remains separately account-aware. No write behavior is introduced.

## Next approved validation

Resolve dependencies and compile with the real Android toolchain; run new `StoragePolicyTest` and existing suites. Validate OAuth registration/custom redirects on actual devices before release, browser cancellation/denial/duplicate or bad-state callback/process loss, expired tokens, reconnect/account-switch/disconnect races, restricted owner files and provider rate limits. Test OneDrive paging and Dropbox app-folder/full-access behavior. Confirm bearer headers never appear on content-host redirects and signed URLs never reach history/logs. Test SMB3 encryption/signing, intentional unencrypted SMB2/3, rejected SMB1/guest servers, large listings, invalid credentials, disconnect/Stop races and file range seeking. Check safe insets, large fonts, short-screen dialogs, keyboard, TalkBack and actual native playback of supported media. No runtime/performance/security-certification claim is made by source review.

## Official references

- https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow
- https://learn.microsoft.com/en-us/graph/api/driveitem-list-children?view=graph-rest-1.0
- https://developers.dropbox.com/oauth-guide
- https://www.dropbox.com/developers/documentation/http/documentation
- https://github.com/hierynomus/smbj
