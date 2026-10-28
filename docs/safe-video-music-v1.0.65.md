# Safe browser playback, music recovery, compact downloads — 1.0.65 / 66

## Changes
- Filename, bytes/status, speed/ETA, pause/open, and per-file menu now share the same horizontal download row and bordered card. Progress stays directly underneath; no separated controls row.
- Removed automatic website-to-native playback. Old bridge calls always return false; the new detection script neither launches Hi Player nor pauses videos. Unrelated page gestures no longer authorize playback.
- Website direct-media navigation is consumed without replacing the original page. A direct user click can show a native confirmation; scripts/redirects cannot launch the native player. The browser menu and download sheet retain explicit playback actions.
- Known advertising hosts, ad media paths, and VAST/VPAID/ad-tag URLs are rejected from native playback and detected media candidates. Existing request filtering remains enabled. This is not a claim that every possible advertisement can be identified.
- Video settings now say Detect website videos and explain that playback is manual.
- Music scans include all indexed audio, including downloads/recordings with IS_MUSIC=0, short/unknown durations, and absent optional album/artist/path columns. Failed providers remain incomplete and cannot wipe the durable library.
- Scan readable public Music/Download/Recordings folders as well as existing messenger folders; metadata is cached by size/modification time to avoid repeated extraction.
- Music tab explicitly offers its separate audio permission and app-settings recovery, including on existing installations. Returning from Settings refreshes access. Empty/filtered lists offer Scan music / Show all music.

## Device QA
1. Upgrade without uninstalling; open Music. If requested, allow music/audio access (separate from video/photo access), then Scan music.
2. Verify MP3/M4A/FLAC/OPUS and short recordings appear, including downloaded files without music metadata. Permission denial should show recovery controls rather than a misleading empty list alone.
3. Open a site with autoplay/pre-roll/popup video: no automatic native-player launch and original page remains available. Explicit menu playback still works for the chosen main video.
4. Tap an actual direct-video link: confirm or stay on page; cancelling must retain the original URL/history.
5. Start a large download on a narrow phone: details, per-file speed/ETA, and controls share one compact row; pause/resume and menu actions remain usable.

Android storage restrictions still apply. Private app-only files and inaccessible folders cannot be enumerated without an appropriate grant. Real-device/server-specific checks remain required in addition to local regression tests.

## Local validation
- assembleDebug passed, including vital lint; APK package version and signature verified.
- 65 selected regression tests passed with zero failures: existing download/playback/library tests, audio provider/optional-column tests, ad URL/navigation/legacy bridge/script safety tests, and updated Compose per-file row assertions.
- Compose assertions verify speed/ETA is inside the same horizontal row as the filename, not in a separate controls row.
- No claim of physical-device verification; permission/OEM/provider and actual ad-heavy website checks remain in the checklist above.
