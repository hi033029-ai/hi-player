# Audio lyrics reliability (source only)

- One shared parser now renders plain lyrics as well as timed LRC in the expanded and compact audio player. Supports multiple timestamps, whole seconds, 1–3 fractional digits, offsets, UTF-8 BOM, and Unicode text.
- Timed lyrics follow playback position, including seeks; plain lyrics remain manually scrollable without invented timing. Artwork shading no longer covers lyrics or the retry action.
- Lyrics control preserves its enabled state across track changes. Lookup happens only after enabling lyrics, not for every track while disabled. Turning it off or changing tracks cancels outstanding lookup; a generation/URI guard prevents stale results replacing current lyrics.
- Loading, unavailable, instrumental, and connection-error states are visible. Retry requests the current track. Unknown artist metadata and downloaded Artist - Title filenames receive improved matching. Search results must match title/artist and, when available, duration instead of blindly selecting the first result.
- Lookup order: readable same-basename local .lrc, bounded app cache, LRCLIB exact/search, then lyrics.ovh plain fallback when artist is known. Cached success works offline. Provider failures do not abort later fallback requests; requests have timeouts, cancellation, and response-size limits.
- Local .lrc support requires a readable physical/file path. SAF-only content URIs cannot discover adjacent sidecars without separate folder access. Provider coverage is not universal; lyrics are never fabricated. Online lookup sends song title/artist and available duration to providers after the user enables Lyrics.

## Validation and limits

- A public LRCLIB exact request for Coldplay / Yellow returned HTTP 200 with synced and plain lyrics (1,533 and 994 characters respectively). This checks endpoint availability, not Android execution or catalog-wide coverage.
- Added parser, query-match, and Compose display/retry regression sources. They have NOT been executed. No Gradle, compile, tests, APK build, version bump, or main merge performed for this request.
- Device verification remains needed: online toggle, seek sync, plain-only song, rapid Next/Previous, lyrics off during lookup, retry after loss of network, readable local sidecar, and offline cache playback.
