# Hi Player FFmpeg decoder extension

Hi Player already enables Media3 extension renderers with `EXTENSION_RENDERER_MODE_ON` and decoder fallback in `HiPlayerEngine`. That means a locally built FFmpeg extension is selected only when the device has no usable hardware decoder.

## Build the extension

The extension is built from the matching AndroidX Media3 checkout. Install Android NDK r26 or newer, then run:

```bash
git clone https://github.com/androidx/media.git
cd media/libraries/decoder_ffmpeg/src/main
export NDK_PATH="$HOME/Android/Sdk/ndk/26.1.10909125"
./build_ffmpeg.sh \
  "$(pwd)" \
  "$(pwd)/jni/ffmpeg" \
  "$NDK_PATH" \
  linux-x86_64 \
  --enable-decoder=truehd,eac3,ac3,dca,mlp,flac,vorbis,opus
```

This produces native FFmpeg libraries below `jni/ffmpeg/android-libs/<abi>/`.

## Include it in a local checkout

From the Hi Player repository root, add the Media3 module to `settings.gradle.kts`:

```kotlin
include(":media3-ffmpeg-decoder")
project(":media3-ffmpeg-decoder").projectDir =
    File("../media/libraries/decoder_ffmpeg")
```

Then add the module dependency in `app/build.gradle.kts`:

```kotlin
implementation(project(":media3-ffmpeg-decoder"))
```

After syncing, verify the generated APK contains the FFmpeg native libraries under `lib/<abi>/`. Hi Player will try hardware decoding first and use FFmpeg as a fallback for formats such as TrueHD that are unsupported by the device decoder.

## Distribution note

FFmpeg is LGPL when dynamically linked and its corresponding source/relinking obligations must be preserved. Dolby TrueHD is separately subject to codec patent considerations; this document does not grant a patent license.
