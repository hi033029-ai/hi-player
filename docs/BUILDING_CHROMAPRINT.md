# Building Chromaprint for Android (native fingerprinting library)

Chromaprint has no Android/Gradle-ready prebuilt — like the FFmpeg extension earlier,
it needs a one-time native build with the NDK.

## Prerequisites
- Android NDK + CMake (already installed if you did the FFmpeg build earlier)
- `git`

## Steps

```bash
# 1. Clone Chromaprint's source
git clone https://github.com/acoustid/chromaprint.git
cd chromaprint

# 2. Build for each ABI you ship. FFT_LIB=kissfft avoids needing a separate FFmpeg/
#    FFTW dependency — chromaprint bundles KissFFT as a lightweight built-in option,
#    which is the simplest path for a mobile build.
mkdir build-arm64 && cd build-arm64
cmake .. \
  -DCMAKE_TOOLCHAIN_FILE=$NDK_PATH/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DFFT_LIB=kissfft \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_TOOLS=OFF -DBUILD_TESTS=OFF
cmake --build .
# Produces libchromaprint.so — copy it to app/src/main/jniLibs/arm64-v8a/
# Repeat with -DANDROID_ABI=armeabi-v7a and x86_64 for those jniLibs folders.
```

```bash
# 3. Also copy the public header so the JNI wrapper below can include it:
cp ../src/chromaprint.h ../../app/src/main/cpp/chromaprint.h
```

Then wire `chromaprint_jni.cpp` (below) into your build via CMakeLists.txt, linking
against the `libchromaprint.so` you just built.

## AcoustID API key

Free, instant, no card — register at https://acoustid.org/api-key (there's a
checkbox for open-source/non-commercial use, which covers a personal project like
this). You'll get a short client key string to put in `AcoustIdApi.kt`.

## Honest expectations

AcoustID's fingerprint database is community-contributed via MusicBrainz — it's
genuinely free forever with no request caps, but coverage skews toward more
well-known/older tracks that people have actually submitted. Expect a noticeably
lower hit rate than a commercial service on brand-new releases or niche tracks —
that's the real tradeoff for zero ongoing cost, not a bug in the implementation.

## Wiring CMake into Gradle

`app/build.gradle.kts`:
```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}
```
CMakeLists.txt (place at `app/src/main/cpp/CMakeLists.txt`, alongside
`chromaprint_jni.cpp` and `chromaprint.h`) is provided as its own file.
