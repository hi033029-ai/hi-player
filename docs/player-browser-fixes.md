# Player and browser fixes (source-only)

Source-only change set on `feature/player-browser-fixes`. Nothing was built,
linted or tested; no version bump. Needs a device QA pass before release.

## Player
- **Crash while playing**: screens now follow the engine's *current* ExoPlayer
  (`HiPlayerEngine.playerInstance`), so the video surface, HDR manager, seek
  controller and media session never keep using a released player after the
  engine rebuilds it. `GlVideoSurface` detaches its surface synchronously,
  releases the SurfaceTexture only after the GL thread stops, and retires old
  surfaces safely when the GL context is recreated. The buffer target scales
  with the heap (32–192 MB) instead of a fixed 192 MB, and `largeHeap` is on.
- **Skip (double-tap / buttons)**: every tap seeks immediately; quick taps add
  up from the last target. Network streams seek to the nearest key frame
  (fast start); local files keep exact seeking. 15 s back buffer makes short
  rewinds instant. `seekTo` no longer clamps to 0 before the duration is known.

## Browser
- **Tabs kept**: tabs live in a session object; leaving browser mode keeps the
  active page loaded (paused) and other tabs restore from history. Regular
  tabs are also saved to disk and come back after the app restarts (private
  tabs never are).
- **Loading / network**: a loading page with a "network seems slow" hint and
  Stop/Retry replaces the blank or stale view; network failures show an error
  page with Retry, and pages retry automatically when the connection returns.
  Offline, cached pages still open.
- **Faster search**: history writes moved off the main thread, history is
  cached in memory, suggestions are only computed while the address bar is
  focused, and the WebView engine is warmed up on the start page.
- **Ad blocking**: new `orbit_adblock.js` page shield (ad slots, click-hijack
  links, standard-size ad frames, ads inside HTML5 players); Peter Lowe's list
  added to EasyList/EasyPrivacy (stored lists refresh automatically); EasyList
  URL-fragment rules on sub-resources; more cosmetic rules; more ad-network
  hosts; ad pop-ups and new tabs blocked.
- **Time skipper**: timers already running when a countdown page is detected
  are re-scheduled at 30x right away, and clock-based countdowns (Date,
  performance.now, requestAnimationFrame) run fast too. Server-checked waits
  cannot be skipped.
- **Auto-play in Hi Player** (Settings > Videos, on by default): a video the
  user starts on a page opens in Hi Player when it is large, not inside an ad
  container, not on an ad host/URL, and live or at least 45 s long. Pre-rolls
  stay on the page; the real video after a pre-roll can still open. Tapped
  links to video files play directly.
- **Cleanup**: removed duplicate/unused files (`ui/PlayerControlsOverlay.kt`,
  `ui/AnimatedPlaybackButtons.kt`, `ui/HdrIcon.kt`, `ui/SpeedOverlayIndicator.kt`,
  `ui/AspectRatioPickerOverlay.kt`, `ui/ResizableVideoSurface.kt`,
  `assets/adblock_hosts.txt`) and unused browser helpers.

## Device QA checklist
1. Play local HEVC/HDR and a browser HLS stream; switch decoder mode, rotate,
   background/foreground, PiP — no crash, picture returns.
2. Double-tap skip repeatedly on local and streamed video — moves at once.
3. Open 3 tabs, go to Library, come back — same tabs, active page not reloaded.
   Force-stop the app and reopen the browser — regular tabs are back.
4. Airplane mode → open a page → error page; turn network on → reloads.
5. Start a long video on a streaming site → opens in Hi Player; a pre-roll ad
   does not. Turn the setting off → stays on the page.
6. Countdown/link pages finish quickly; normal sites unaffected.
7. Sites with pop-unders/banners: no ad tabs, fewer banners, nothing broken on
   Google, YouTube, Wikipedia, shopping and sign-in pages.
