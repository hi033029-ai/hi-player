#!/usr/bin/env bash
set -euo pipefail
out=${1:-app/src/debug/assets}
mkdir -p "$out"
# 32 seconds, multiple GOPs, and two audio languages: not a tiny startup-only clip.
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=640x360:rate=24' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000' -f lavfi -i 'sine=frequency=880:sample_rate=48000' \
  -t 32 -map 0:v -map 1:a -map 2:a -c:v libx264 -preset ultrafast -g 24 -pix_fmt yuv420p \
  -x264-params 'colorprim=bt709:transfer=bt709:colormatrix=bt709:range=limited' \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 -color_range tv \
  -c:a aac -metadata:s:a:0 language=eng -metadata:s:a:1 language=ara -movflags +faststart -y "$out/gecko_test.mp4"
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -map 0 -c copy -cluster_time_limit 1000 -y "$out/gecko_test.mkv"
# No final Cues/duration, streaming unknown-size Segment.
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -map 0 -c copy -live 1 -cluster_time_limit 1000 -y "$out/growing_test.mkv"
ffmpeg -hide_banner -loglevel error -i "$out/gecko_test.mp4" -an -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -y "$out/playback_test.webm"
# A stationary colour reference separates a real grade from temporal motion
# while a paused frame travels through Media3's asynchronous output pipeline.
ffmpeg -hide_banner -loglevel error -f lavfi -i 'smptebars=size=640x360:rate=24' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 8 \
  -c:v libx264 -preset ultrafast -g 24 -pix_fmt yuv420p \
  -x264-params 'colorprim=bt709:transfer=bt709:colormatrix=bt709:range=limited' \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 -color_range tv \
  -c:a aac -movflags +faststart -y "$out/grading_reference.mp4"
# Fail if a generated "SDR reference" silently lost its transfer/gamut tags.
python3 - "$out/gecko_test.mp4" "$out/grading_reference.mp4" <<'PYVERIFY'
import json, subprocess, sys
for path in sys.argv[1:]:
    stream = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_streams", "-of", "json", path]))["streams"][0]
    assert stream["color_transfer"] == "bt709", stream
    assert stream["color_primaries"] == "bt709", stream
    assert stream["color_range"] == "tv", stream
PYVERIFY
# Exercise native Main10/PQ metadata, decoder startup and seeking on CI at a
# modest resolution. The UHD fixture below remains available for real panels.
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=320x180:rate=15' \
  -t 18 -an -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le -g 15 \
  -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -color_range tv \
  -x265-params 'pools=2:frame-threads=2:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:hdr10=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400' \
  -y "$out/compact_hdr10.mkv"
ffprobe -v error -select_streams v:0 -show_entries stream=profile,color_transfer \
  -of json "$out/compact_hdr10.mkv" | python3 -c 'import sys,json; s=json.load(sys.stdin)["streams"][0]; assert s["profile"] == "Main 10" and s["color_transfer"] == "smpte2084", s'

# Optional physical-device fixtures. A 4K/Main10 decode is not emulated HDR evidence.
if [ "${HI_PLAYER_PHYSICAL_FIXTURES:-false}" = true ]; then
  ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=3840x2160:rate=24' -t 20 \
    -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le -g 24 \
    -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -color_range tv \
    -x265-params 'pools=2:frame-threads=2:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:range=limited:hdr10=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400' \
    -an -y "$out/playback_main10_hdr10.mkv"
fi
