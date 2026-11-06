# Gecko and AV1 Android runtime probe

This is an isolated test app, not the Hi Player browser or a release build. It exercises a GeckoView 159 x86_64 session loading a local page and the experimental Media3 1.11 / Libgav1 bridge decoding a generated local AV1 video. It is intended for an x86_64 API 26+ Android emulator. It does not test authentication, the Hi Browser UI, downloads, other ABIs, or the complete media player.

From the repository root, prepare the app's existing decoder and a generated test pattern:

```sh
mkdir -p experiments/gecko-browser/runtime-probe/app/libs experiments/gecko-browser/runtime-probe/app/src/main/assets
cp app/libs/media3-av1-decoder-release.aar experiments/gecko-browser/runtime-probe/app/libs/
ffmpeg -f lavfi -i 'testsrc2=size=160x90:rate=8' -t 1.5 -c:v libaom-av1 -cpu-used 8 -crf 40 -an -y experiments/gecko-browser/runtime-probe/app/src/main/assets/test-av1.mp4
```

With Android SDK platform 37.2, Java 17+, Gradle 9.3.1 and an x86_64 API 26+ emulator installed, run:

```sh
cd experiments/gecko-browser/runtime-probe
gradle :app:connectedDebugAndroidTest
```

The generated files, test APKs and test media are local only. Emulator results cannot substitute for testing Hi Player's real app and physical devices.

## Observed result

On a software-emulated API 28 x86_64 Android device, the Gecko test loaded its local HTML page successfully. The AV1 test aborted in native initialization: the bundled 1.5.1 Libgav1 JNI looks up `VideoDecoderOutputBuffer.decoderPrivate` as an `int`, while Media3 1.11 declares it as `long`. The experimental legacy-renderer bridge is therefore **unsafe**, even though its registration-only Robolectric tests pass. Use a compatible, rebuilt decoder instead; do not use the bridge in production. A passing single-page Gecko smoke test is not browser feature-parity validation.
