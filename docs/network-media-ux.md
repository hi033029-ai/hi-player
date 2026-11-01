# Optional network media — source-only follow-up

Status: uncompiled. Tests authored, not executed. No device screenshots, live server checks, main merge, version bump, APK or release. Feature branch / draft PR only.

## Experience

Settings has a separate Google account/profile section and one collapsed **Connections** card containing Google Drive, Jellyfin and WebDAV. All three are full-row, name-first entry points. Google Drive opens the existing account-verified Drive browser and requests its own authorization; Google sign-in alone is not presented as Drive access. Jellyfin/WebDAV profile management is available underneath. No server or storage account is needed to use local playback.

- Tap an unconnected Jellyfin row: enter your server address → request a Quick Connect code → approve in an already signed-in Jellyfin app or web interface → open your native library in Hi Player. A user-triggered Custom Tab opens the server's web interface for approval; no WebView cookie/token scraping. Disabled/unsupported Quick Connect can use username/password instead.
- Quick Connect polls every five seconds with a two-minute coroutine timeout. A blocking network request can take until its socket timeout to unwind; cancellation is checked before saving. Dismiss/Back cancels the setup job; approval already performed remotely is not undone.
- Jellyfin stores its encrypted device token, not the login password. Migration removes old saved Jellyfin passwords. Saved profiles verify the token through `/Users/Me`; revoked sessions need fresh sign-in. Quick Connect device identity is retained with the token.
- Tap WebDAV: enter the provider's direct WebDAV folder address, source label and credentials/app password. Verify that folder, then browse it. WebDAV has no universal website OAuth flow. Existing encrypted credentials remain necessary for direct requests.
- HTTPS is preferred. Explicit HTTP setup needs an unencrypted-connection acknowledgement. Redirecting server addresses are rejected during authentication/listing; use the final direct server/folder address. TLS failures are never ignored.
- Connected rows open libraries. Jellyfin browses server libraries and nested folders, with 60-item pages. WebDAV browses selected-folder descendants one level at a time, at most 1,000 displayed entries per folder and a 4 MiB XML response; larger responses fail with safe recovery copy. There is no expensive recursive scan just to connect or open a folder.
- Tap a supported video/audio file for Hi Player direct playback. No transcoding negotiation, Jellyfin watch-state synchronization, full server dashboard mirror, thumbnails, casting integration or automatic server discovery is promised in this first implementation. A codec/device/direct-play incompatibility remains possible.
- Back follows actual folder history. Session-only cache (20 pages) and scroll memory preserve recently visited folders while connected. First load uses a file-list skeleton; Refresh preserves existing content. Stop/Retry/Manage remain available. No personal server URL, password or token enters saved-state Bundles.
- Manage holds saved profiles, add another, browse and confirmed disconnect. Disconnect revokes local active header scopes including legacy per-file scopes; it does not delete server content, revoke sessions on the server, or forget saved profile copies. Forget removes a saved copy only.

## Playback credential boundary

Server playback uses a dedicated range-capable data source. Authorization headers are selected by exact scheme/host/effective port and path boundary. A connection change fails an already-open authorized source closed. Redirects never receive credentials for another configured origin, never downgrade HTTPS, never use URL credentials, and never regain credentials after leaving the original origin. Same-origin redirects only receive the original expected header set within an authorized path. Media URLs/history contain no server token. Google Drive keeps its separate account-aware implementation and is not routed through these server headers.

## Official references

- https://jellyfin.org/docs/general/server/quick-connect/
- https://kotlin-sdk.jellyfin.org/guide/authentication.html
- https://api.jellyfin.org/

## Next approved validation

Compile and run new NetworkMediaPolicy / RemoteStreamHeadersRegistry regression cases plus existing suites. Then test against real Jellyfin and WebDAV servers: enabled/disabled/expired/denied Quick Connect, base-path installations, password fallback, token revocation and profile switching, redirects, TLS/HTTP acknowledgement, large and malformed listings, audio/video playback and seeking, server redirect ranges and no credential leaks, slow/offline/double-tap/cancel races, restore/Back/cache behavior, narrow/landscape screens, large fonts and TalkBack. No authentication or performance claim is runtime-verified by this source pass.
