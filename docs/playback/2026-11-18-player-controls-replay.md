# Player controls and completed-file replay

Baseline: 3f42c844921ec34b55fa37c5a137f29d3a34a1c0. Recording: 7609.mp4 (42.86 seconds).

The first video opens to a black picture at 01:34/01:34. The next video starts decoding later, and picture-mode changes interrupt output. The recording cannot establish the phone's ANR stack or physical HDR color accuracy.

## Changes

- Restore the earlier left group (rotation, floating window, HDR) and right group (speed, audio, equalizer), including the HDR label. Retain whole-button taps, selected-mode accessibility state and long press.
- Keep centred transport and edge utilities in landscape. Below the width needed for all nine targets, put utilities and transport in separate rows. Transport targets are 48dp; labels wrap, and the secondary row scrolls on narrow screens.
- Discard seekbar scrubs on Android cancellation or an added finger. A synthetic consumed up event must not commit a seek. Increase the seekbar touch area.
- Reopen completed videos from zero when saved progress is within their final two seconds (a smaller window for short clips). Growing sources and explicit starting positions keep their positions.
- Play after actual EOF reopens LibVLC media or seeks Media3 to zero. Seeking backward after EOF remains distinct from replay. Clear ended playback intent in both backends.
- Avoid saving the previous decoder's clock under a newly selected URI while settings/probing is pending. Capture aspect ratio before scheduling the database write.
- Preserve track choices across picture-pipeline replacement without restoring native tunneling into the graded pipeline. Local decoder setup now displays truthful transient loading feedback.
- Pin device CI to Ubuntu 24.04. The previous API 30 job timed out installing FFmpeg from the moving ubuntu-latest image; API 24 timed out installing the APK before tests. Retain both device suites and FP16 checks, and give the emulator adequate memory/data space.

## Validation

Pending until the matching CI revision completes. Unit coverage checks real pointer clicks and non-overlapping bounds in portrait, landscape and enlarged text, cancelled/multi-touch seeks, and completed versus growing resume positions. Full-screen Media3/LibVLC device cases start with saved EOF progress, exercise swipes, reach actual EOF and require decoding after Play. Existing growing Matroska, picture-mode pixel, browser and numeric FP16 checks remain enabled.

Physical phone ANR reproduction, HDR10/Dolby Vision output and the user's original media files remain separate from emulator evidence.
