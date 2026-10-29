# Music toolbar, Stream & Cast and browser navigation polish

Source changes after 1.0.68 (69). Version unchanged. No Gradle, compilation, APK build or tests run for these changes; prior 81-test release result does not cover them.

## Music
Single compact toolbar contains horizontally scrollable Songs/Folders/Playlists/Albums/Artists plus fixed 48dp view/sort controls. Removed the permanent second control row and per-category border frames. Selected text uses black/white contrast against the actual accent (fixes white-on-white selected category). Collection back navigation appears only when inside a collection.

## Discover
Removed the destination, enum entry, header action, route and hub label. No automatic catalog loading on settings initialization. Backend metadata types and personal-source credential management remain for compatibility and to let users remove previously saved server credentials; no Discover tab remains.

## Stream & Cast
Replaced the nested-card AlertDialog with a centered, bounded, scrollable, keyboard-aware Stream & Cast window: paste-on-tap URL input, local playback action, official Cast route picker, connection status, explicit cast action, remote play/pause/stop/disconnect and truthful limitations/errors. Uses Google's Cast framework 23.0.0, default media receiver, declared OptionsProvider and FragmentActivity support for the official picker. AppCompat theme wrapper is limited to the route button/dialog context.

The TV fetches the URL itself. No local-file server, arbitrary TV discovery, screen mirroring, protected-Drive casting, cookie forwarding or private request-header forwarding. Rejects credential URLs/OAuth query tokens and loopback addresses. Direct stream reachability, supported codecs and a compatible receiver on the same network are required. No invented Cast receiver ID.

## Browser navigation
Moved accessible reveal button to bottom-right (above address bar on web pages; near the lower corner in full-screen library). Retains bottom-edge swipe. Replaced restart-on-every-event idle effect with a monotonic clock-driven five-second loop. Only press/release or movement while pressed counts as activity; hover/synthetic unchanged events cannot extend the timer. Physical device reproduction is still required before claiming the reported timeout issue resolved in an APK.

## Source-only checks
- git diff --check; AndroidManifest XML parsed.
- Inspected official Google SDK/MediaRouter AAR signatures with javap (no app compilation): CastContext access, RemoteMediaClient load/callbacks, SessionManagerListener, MediaRouteButton.setAlwaysVisible.
- Added timer/event policy, music contrast and Cast URL policy test source; not executed.

## Validation required after explicit approval
Compile and regress previous auth/Drive/player/browser/download/library tests plus new test sources. Test portrait/landscape, large text, white/black/custom accents, scrollable category strip and view controls, timer while idle/hover/touch, corner-button hitbox and keyboard, browser downloads/tab restoration. On physical Cast-enabled phone and receiver: chooser, connect/cancel/reconnect/suspend, media load/error/codec handling, pause/stop/disconnect, app background and recreation. Check local playback and PiP with FragmentActivity; no device validation has been performed.

References:
- https://developers.google.com/cast/docs/android_sender
- https://developers.google.com/cast/docs/android_sender/integrate
