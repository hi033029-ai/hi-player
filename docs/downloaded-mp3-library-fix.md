# Downloaded MP3 discovery

Source-only follow-up after an MP3 was missing from Music. No new build, compilation, test run, main merge or release performed. Version remains 1.0.69 (70).

## Gap found
Browser downloads publish to Android's Downloads collection, then request asynchronous media scanning. Music previously queried/watched Audio MediaStore only, plus readable filesystem fallback directories. On scoped-storage devices an app-owned Downloads/SAF URI can be readable while filesystem access is unavailable and Android has not created its Audio index entry. Unlike videos, completed browser audio had no direct library integration or completion-triggered music refresh.

## Changes
- Merge completed, accessible app-owned audio downloads into the music scan using their saved content URI. Retain that URI for playback; do not depend on filesystem paths or move files from Download.
- Exclude in-progress/paused/failed/cancelled downloads, missing destinations, empty files, video and HTML error responses. Do not expose private partial files as tracks.
- Prefer existing indexed audio when deduplicating by available path; fallback discovery remains available for other readable shared-storage audio.
- Refresh Music on distinct completed-audio changes (including restore/rename/removal), not each download progress update. Observe the Downloads collection too, with the same existing coalescing refresh and lifecycle cleanup.
- Normalize common MP3 MIME aliases to audio/mpeg. Include audio in legacy completed-media format repair and rescanning, which previously handled video only.
- Previously completed downloads are loaded from the existing journal on startup; no redownload is required when their saved URI is still accessible.

Regression source added, not executed: CompletedAudioLibraryTest covers an accessible content-only MP3 without Audio indexing/DATA, deletion, incomplete statuses, MIME aliases/generic MP3 and non-audio rejection.

Device checks after build approval: download MP3 to Download and an authorized SAF folder; verify immediate Music appearance and playback without reopening tabs. Restart with the old download present. Check no duplicate appears when Android later indexes the same path. Test denied general audio permission with an app-owned readable URI, revoked SAF access, deletion, renamed MP3s and background downloads. Audio downloaded by another app still requires Android media indexing, readable shared-storage access or an explicit file/folder grant; this does not bypass Android permissions.
