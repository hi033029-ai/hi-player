# Playback repair and evidence

Base: `47dec5939604986eda850ad4058b4a05c681157a`, branch `feat/geckoview-browser-migration`.

## Confirmed source defects

| File / method | Defect | Change |
| --- | --- | --- |
| `VlcPlaybackEngine.attachRequestedSurface/detachSurface/release` | Main-thread AWindow operations overlapped native stop/release. In bundled LibVLC 3.7.7, holder destruction invokes AWindow.detachViews -> MediaPlayer.onSurfacesDestroyed -> native video-track disable synchronously. Creation may synchronously invoke play/video-track enable. | Single native handler; explicit stop acknowledgement before main surface operations; raw Surface binding without registering AWindow's holder callbacks; release after detach acknowledgement. No waits or thread joins on main. |
| `VlcPlaybackEngine` event listener | Generation was sampled when an old event was delivered, allowing events from a previous load to act on a replacement media item. | Listener captures the load revision. Stopped input events cannot recreate media or switch engines. |
| `VlcPlaybackEngine` startup watchdog | Armed after native preparation, so a blocked preparation/play could prevent the watchdog itself from starting. | Main starts the watchdog before native work. A nonresponsive native instance is quarantined; no replacement thread/player loop. |
| `HiPlayerEngine.prepareMedia` | All local MKV including incomplete/Main10/HDR were forced to VLC; its external cluster index was never supplied to the VLC demuxer. It could advertise seekability based solely on that index. | Growing Matroska uses Media3's byte-addressed extractor. Completed AVC Matroska retains VLC; completed HEVC/DV/other color-sensitive streams use Media3 after a bounded off-main metadata probe. |
| `FileDescriptorDataSource` | Reopened descriptors on every append and content-provider polling. FileInputStream and ParcelFileDescriptor both closed the same fd. Interrupted EOF polling could spin. | Sole AutoCloseInputStream owner, persistent descriptor and channel size queries; interruptible EOF waits on loader; bounded idle failure; explicit DataSpec length retained. Open fd survives atomic rename. |
| `GrowingMkvSeekMap` | UI queries opened/scanned files under a synchronized index lock. Scanner subtracted 256 KiB even for tiny valid prefixes, rejected unknown-size Clusters, and accepted timestamps without any video keyframe. | Loader scans EBML child headers, recognizes unknown-size sibling boundaries, reads video track/keyframe metadata, publishes immutable snapshots and updated seek maps. UI queries perform no I/O or scanner locking. |
| `PlayerScreen` / VLC color options | Explicit presets routed HDR to an RGBA8 OES shader without transfer/gamut handling, or installed VLC's SDR adjust filter. | One Media3 color processor receives linear BT.2020 HDR/BT.709 SDR, uses HDR FP16 buffers, and leaves transfer/range/tone mapping with Media3/platform. Identity output keeps native SurfaceView and never calls `setVideoEffects(emptyList())`: in Media3 1.11 that call itself enables GL. Entering/leaving grading explicitly rebuilds the renderer once at the last decoded position with track overrides, speed, volume and subtitles retained; changing between grades only updates the active shader. |
| `PlayerStartupThreadTest` / old smoke tests | Provider access was never awaited; native test accepted layout or Vout as proof of display; fixtures lasted 3 seconds. | Blocking-provider heartbeat, displayed-picture counters for VLC, rendered-frame callbacks and decoded PTS for Media3; 32-second fixtures, actual temporary EOF, append, seeks, track change, surface replacement and invalid-file error. An SDR PixelCopy test compares the same decoded frame with a preset and after restoring Original. |

These are verified source defects. **No physical-phone ANR trace has been supplied or captured here. The original phone's exact blocked native stack is not verified.** A vendor codec stuck in JNI cannot safely be killed in-process; the design keeps that single instance out of the UI's execution path and avoids creating replacements indefinitely.

## Rendering and codec limits

- SurfaceView and a window HDR flag alone do not prove HDR output. `HiPlayerColor` logs actual decoder output standard/transfer/range and the selected input ColorInfo. Confirm SurfaceFlinger layer dataspace and display HDR types independently.
- Identity output installs no compensation, brightness, saturation or VLC adjust filter. Grades use Media3's HDR-aware frame processor; its supported GL fallback owns any required tone mapping. Unsupported renderer/effect combinations must surface an error, never silently drop the user's grade or relabel PQ as SDR.
- The matching Media3 1.11.1 FFmpeg build explicitly enables `truehd eac3 ac3 dca mlp flac vorbis opus`; this establishes build configuration, not lossless fidelity or bitstream passthrough on every receiver.
- Dolby Vision base-layer compatibility belongs to the profile and decoder. Profile 5 must not be represented as an HDR10 base layer. The unconditional MIME-only Dolby Vision -> HEVC fallback was removed from `OptimizedPlayerBuilder.hardwarePreferredCodecSelector`; Media3 selects alternatives using the actual profile.
- VLC's internal audio-device callback also invokes native code on main. VLC now selects decoded multichannel PCM explicitly, leaving device routing to Android. This preserves decoded lossless audio; receiver bitstream passthrough needs separate device/route validation.

## Automated validation

Generate fixtures with `bash tools/generate_playback_fixtures.sh`. They are excluded from git and generated in device CI. `PlaybackDeviceSmokeTest` requires actual decoded/rendered output and a continuous main-loop heartbeat. Browser/account/download regressions remain enabled in Build APK; Gecko device tests remain on API 24 and API 30. Packaging validation still checks the expected ABI, ELF alignment, duplicate C++ runtimes, decoder JNI matching and notices.

Unit cases cover exact EBML offsets/timestamps, incomplete blocks, keyframe absence, unknown-size clusters, retained index descriptors, zero-I/O SeekMap queries, rename/append and cancellation. Temporary EOF does not signal END_REACHED or recreate the player. Malformed parsing errors and abandoned growth have bounded failure paths.

## Required physical-phone acceptance (still outstanding)

Use a real HDR phone, plus an SDR-only device, and a known calibrated HDR10/PQ/BT.2020 limited-range reference, an SDR BT.709 limited/full-range pair, HEVC Main10 4K remux, DV profile 5 and HDR10-compatible profile 8 references, DTS-HD MA and TrueHD samples. The generated HDR fixture is a decoder/metadata stress signal, **not a colorimetric reference**.

1. Record model, Android build, selected codec and decoder output color metadata. Compare Original and graded modes with SurfaceFlinger dataspace/HDR flags and a reference player; screenshots alone are insufficient.
2. Verify MP4 AVC/HEVC, completed MKV AVC/Main10, WebM and UHD first frame and advancing video/audio. Use a 20-second+ 4K initialization and ongoing heartbeat. Enable physical fixture generation with `HI_PLAYER_PHYSICAL_FIXTURES=true` when wanted. Run `PlaybackPhysicalDeviceTest` on the connected phone; missing fixtures fail explicitly.
3. Pause/play/seek rapidly, rotate, background and return repeatedly. Switch native AVC MKV to Media3 HDR and back. Confirm one active surface and correct resume/track/subtitle/speed/mute state.
4. Append a real MKV at Cluster and mid-Block boundaries; pause download >12 seconds, resume, seek inside downloaded keyframes, atomically rename and complete it. Confirm decoded PTS and player identity remain stable through EOF recovery.
5. Switch audio tracks without video restart. Check multichannel decoded PCM and the actual receiver route; do not infer lossless output from the track label.
6. Open invalid, inaccessible and permanently truncated files; confirm controls and navigation stay responsive with an actionable error.
7. Open a video from a Gecko page and from its active download. Verify request headers, tabs, Back navigation and authentication continuity.

While reproducing, run `tools/capture_playback_diagnostics.sh`. It collects filtered player diagnostics, last ANR, codec/display/layer state and a 20-second Perfetto trace. Obtain a user-authorized full bugreport separately if the process thread dump is not accessible on a production build. Inspect main, HiPlayer-LibVLC, VLC input/vout/decoder, Exo playback/loader, Binder and RenderThread stacks together.
