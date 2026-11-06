#!/usr/bin/env bash
# Generate original synthetic test data; never uses copyrighted media.
set -euo pipefail
output=${1:?Pass the target test resources mp4 directory}
mkdir -p "$output"
ffmpeg -v error -f lavfi -i 'testsrc2=size=160x90:rate=12:duration=2' \
  -f lavfi -i 'sine=frequency=440:duration=2' -c:v libx264 -preset ultrafast \
  -pix_fmt yuv420p -g 12 -c:a aac -movflags +faststart -shortest -y "$output/avc.mp4"
ffmpeg -v error -f lavfi -i 'testsrc2=size=160x90:rate=12:duration=2' \
  -f lavfi -i 'sine=frequency=440:duration=2' -c:v libx265 \
  -x265-params 'log-level=error:pools=1:frame-threads=1:keyint=12' \
  -pix_fmt yuv420p -c:a aac -movflags +faststart -shortest -y "$output/hevc.mp4"
