#!/usr/bin/env bash
# Rebuild matching Media3 1.11.1 native decoders without touching Hi Player's app/libs.
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 or newer}"
GRADLE_BIN=${GRADLE_BIN:-gradle}
OUT_DIR=${OUT_DIR:-"$PWD/aligned-decoder-output"}
for tool in git meson ninja nasm "$GRADLE_BIN"; do command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }; done
NDK="$ANDROID_HOME/ndk/27.2.12479018"
[ -d "$NDK" ] || { echo 'Install Android NDK 27.2.12479018' >&2; exit 1; }
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/hiplayer-media3.XXXXXXXX")
trap 'rm -rf "$WORK"' EXIT
fetch() {
  local url=$1 revision=$2 dest=$3
  git init -q "$dest"
  git -C "$dest" remote add origin "$url"
  git -C "$dest" fetch -q --depth 1 origin "$revision"
  git -C "$dest" checkout -q --detach FETCH_HEAD
  [ "$(git -C "$dest" rev-parse HEAD)" = "$revision" ] || { echo "Unexpected revision: $dest" >&2; exit 1; }
}
# All Media3 Java and JNI sources use the same official release commit.
fetch https://github.com/androidx/media.git 8c6678b657ede1e7883fc164ef73ed483c7796c3 "$WORK/media"
AV1="$WORK/media/libraries/decoder_av1/src/main"
FFMPEG="$WORK/media/libraries/decoder_ffmpeg/src/main"
fetch https://github.com/google/cpu_features.git ba4bffa86cbb5456bdb34426ad22b9551278e2c0 "$AV1/jni/cpu_features"
fetch https://github.com/videolan/dav1d.git 42b2b24fb8819f1ed3643aa9cf2a62f03868e3aa "$AV1/jni/dav1d"
fetch https://github.com/FFmpeg/FFmpeg.git 573f2b81ebd97da81def2ba762e81b0328547004 "$FFMPEG/jni/ffmpeg"
# Bound parallelism so builds remain usable on machines with limited RAM.
sed 's/ninja -C "${ABI_BUILD_DIR}"/ninja -j2 -C "${ABI_BUILD_DIR}"/' "$AV1/jni/build_dav1d.sh" > "$WORK/build_dav1d.sh"
sed 's/^JOBS=.*/JOBS=2/' "$FFMPEG/jni/build_ffmpeg.sh" > "$WORK/build_ffmpeg.sh"
bash "$WORK/build_dav1d.sh" "$AV1" "$NDK" linux-x86_64
bash "$WORK/build_ffmpeg.sh" "$FFMPEG" "$NDK" linux-x86_64 21 truehd eac3 ac3 dca mlp flac vorbis opus
export MEDIA3_SOURCE="$WORK/media"
"$GRADLE_BIN" -p "$HERE" :av1:assembleRelease :ffmpeg:assembleRelease --no-daemon --max-workers=2
mkdir -p "$OUT_DIR"
cp "$HERE/av1/build/outputs/aar/av1-release.aar" "$OUT_DIR/media3-av1-1.11.1.aar"
cp "$HERE/ffmpeg/build/outputs/aar/ffmpeg-release.aar" "$OUT_DIR/media3-ffmpeg-1.11.1.aar"
sha256sum "$OUT_DIR"/*.aar
