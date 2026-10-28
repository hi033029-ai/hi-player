# Library persistence, browser Back, and download cards (1.0.64 / 65)

## Changes
- Download confirmation actions have rounded bordered containers, with a highlighted Download action.
- Each download has its own bordered card; speed and remaining time are right-aligned inside that card. No aggregate speed/ETA or duplicate sort/filter/overflow toolbar.
- Per-file pause/resume, rename, details, copy link, cancel, share, open, and video playback remain available.
- New tabs record their opener. Back first traverses WebView history, then closes/unwinds the child to its surviving opener or most recent surviving tab, rather than jumping to the start page. Closing a tab uses the same policy.
- Regular tabs retain bounded WebView history through UI recreation; private tab URLs/history are not serialized.
- Video and music library snapshots are stored atomically in app-private, non-backed-up storage. Partial downloads and remote sample URLs are excluded from durable caches.
- Existing rows stay visible while refresh runs. Failed/unavailable MediaStore queries do not clear the last successful library.
- Audio scans now coalesce rather than cancelling each other, observe MediaStore changes, and refresh periodically while the process is alive. Both libraries refresh on resume.
- Android JobScheduler runs persisted periodic background scans and content-triggered scans for both audio and video. Boot/package replacement restores scheduling.
- Existing native downloader, public Download destination, MIME/container detection, and indexing remain unchanged.

## Limits and device QA
Android schedules background jobs; battery restrictions, revoked permissions, or force-stop can defer scanning. This is not an always-on foreground scanner. The first uncached launch still needs an initial scan. Saved entries may briefly reflect files deleted since the last successful scan.

On a real device, verify:
1. Grant video/audio permissions; open both libraries, switch to Browser and play media, then return: existing rows remain visible while refresh updates them.
2. Close/reopen the app after successful scans: cached rows display before scanning completes.
3. Add/remove local media while browsing; confirm video and music refresh without changing tabs.
4. Open a link in another tab, navigate within it, then press Back: history first, then opener. Repeat after switching app modes/recreating the activity.
5. Start two downloads: each card displays its own speed/ETA; controls pause/resume the correct file.
6. Download MKV/MP4 from an extensionless URL: correct extension/MIME, public Download root, visible in the video library, plays after completion.
7. Reboot and verify background jobs are rescheduled (subject to Android scheduling policy).
8. Test portrait/landscape and large font sizes for card layout and touch targets.

## Local validation
- `assembleDebug` passed; APK signature verified; package version is 1.0.64 / 65.
- Existing downloader/scanning/playback regression selection: 49 tests passed.
- Additional validation: 4 cache/background-job tests and 5 tab-policy tests passed; 2 updated Compose download UI tests passed again. Total distinct selected tests: 58.
- Compose assertions verify per-file stats stay within their card, right of center, and no duplicate global filter/actions toolbar appears.
- Real-device lifecycle, notification, and server-specific download checks remain manual QA, not claims of emulator/device coverage.
