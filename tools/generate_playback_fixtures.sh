#!/usr/bin/env bash
set -euo pipefail
out=${1:-app/src/debug/assets}
mkdir -p "$out"
# 32 seconds, multiple GOPs, and two audio languages: not a tiny startup-only clip.
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=640x360:rate=24' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000' -f lavfi -i 'sine=frequency=880:sample_rate=48000' \
  -t 32 -map 0:v -map 1:a -map 2:a -c:v libx264 -preset ultrafast -g 24 -pix_fmt yuv420p \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 -color_range tv \
  -c:a aac -metadata:s:a:0 language=eng -metadata:s:a:1 language=ara -movflags +faststart -y "$out/gecko_test.mp4"
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -map 0 -c copy -cluster_time_limit 1000 -y "$out/gecko_test.mkv"
# No final Cues/duration, streaming unknown-size Segment.
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -map 0 -c copy -live 1 -cluster_time_limit 1000 -y "$out/growing_test.mkv"
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -an -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -y "$out/playback_test.webm"
# Optional physical-device fixtures. A 4K/Main10 decode is not emulated HDR evidence.
if [ "${HI_PLAYER_PHYSICAL_FIXTURES:-false}" = true ]; then
  ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=3840x2160:rate=24' -t 20 \
    -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le -g 24 \
    -x265-params 'pools=2:frame-threads=2:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:range=limited:hdr10=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400' \
    -an -y "$out/playback_main10_hdr10.mkv"
fi
