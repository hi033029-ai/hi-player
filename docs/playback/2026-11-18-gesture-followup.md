# Completed-file gesture and browser follow-up

Baseline: e3f2360b32cbfc3f2ff7ae6a78deda50a62abb67. User recording: 6290.mp4 (~70 seconds).

The user clarified that the repeated touches are **swipe attempts**, not attempts to cycle HDR presets. The recording opens a completed 21.52 GB UHD remux, advances playback, and shows no seek/brightness/volume feedback during those attempts. It does not establish the original ANR stack or the physical display's HDR color accuracy. The last ten seconds show browser Home/address editing; they do not demonstrate a completed website navigation.

## Confirmed defects and changes

| Location | Defect | Repair |
| --- | --- | --- |
| `GestureOverlay` | Three independent tap/drag/transform recognizers compete for the same pointer stream. Compose transform detection consumes one-finger pan even though the callback uses only zoom. | One recognizer arbitrates taps, directional swipes, holds, and two-finger pinch. Direction and brightness/volume side lock at touch slop. Cancellation and a second finger discard a seek preview rather than committing it. Android ACTION_CANCEL synthesizes consumed up events in Compose; the handler must check consumption before committing a release. |
| `PlayerViewModel.onVolumeGesture` | Every tiny delta is truncated to an integer volume/boost step; realistic movement events repeatedly produce zero. | Accumulate a floating point target for the complete swipe, then round the absolute target. Rebase at each swipe so hardware volume changes are respected. Downward movement can cross from boost into ordinary system volume. |
| `ControlsOverlay.PlayerToolbarTextButton` | Only the small icon is clickable; its label is outside the action. Fixed height and 7sp labels hinder use. | Whole 48dp-minimum action accepts taps, with larger wrapping labels. Picture control shows the selected grade or Original. |
| `DownloadingFilesRegistry.observeFinalNameDownload` | A completed Download video with a fresh mtime is automatically registered as incomplete, losing normal Cues/duration/seek routing. | Require an observed size increase. Explicit owned/pending downloads retain their registration. This is a source-confirmed additional defect, not a proven cause of the recording's swipe failure. |
| `ColorPresetEffect` | `pow(linearHdr, contrast)` severely reduces typical normalized HDR values and shifts RGB ratios. | Bounded luminance contrast preserves black and chromaticity; gain stays between 1/k and k for positive contrast k. Original continues to bypass grading. Transfer, gamut and tone mapping remain owned by Media3/platform. SDR uses Media3's configured working space (not assumed linear across library versions). |
| `GeckoBrowserShell` / `GeckoBrowserChrome` / `GeckoBrowserEntry` | Shell address/shortcut navigation bypasses the host's direct-media handoff; editing can retain focus while opening menus/tabs, and Back history can outrank editing. | Share host navigation, including library/QR actions; clear editing focus on submit/Home/menu/tabs, key the address editor by tab, and close editing before navigating history. |
| `HiBrowserHome` | Repeated framed panels and framed shortcut tiles crowd the start page; bottom clearance assumes a fixed toolbar size. | Remove redundant home frames, reduce the large identity header, and reserve measured toolbar height. Bookmarks, shortcuts, private tabs, search providers, menus and navigation remain present. |

## Regression coverage

- Compose pointer tests: horizontal seek, left brightness, right volume, cancellation, pinch, screen lock, and double tap without a stray single tap.
- ViewModel/AudioManager regression: 100 sub-step movement events must produce audible system-volume steps; boost transitions and rebasing are checked.
- Completed 21 GB registry regression: fresh mtime alone cannot select growing playback; an actual later size increase can.
- Browser Compose regression: typed media reaches the host, submit/menu/tab dismissal clears focus, and Back while editing keeps page history intact.
- Device suite now drives the **complete production `PlayerScreen`**, including overlay hit testing and real decoder surfaces, for completed MP4/Media3 and completed AVC MKV/LibVLC. Checks authoritative decoded seek positions, actual window brightness and Android audio volume.
- Numeric GPU regression runs the production shader on dark/mid/high gray values in SDR/HDR shader modes, checking retained shadow detail, neutral gray and preserved pivot. This does not verify a physical HDR output chain.
- Real Gecko device test uses the production browser entry and local HTTP server: address submission, new/select tab, bookmark/Home reopening and direct video handoff.

Build APK remains gated on API 24/30 device suites. No failing test is skipped. Hardware HDR10/Dolby Vision, the user's 4K remux and the reported phone ANR still require reproduction on that phone; emulator decoded playback and numerical shader tests are separate evidence.
