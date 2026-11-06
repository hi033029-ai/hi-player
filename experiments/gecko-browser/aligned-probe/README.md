# Aligned Gecko and decoder playback probe

Isolated test app, **not** the Hi Player browser or an installable release. It uses GeckoView 159 x86_64 and Media3 1.11.1 with matching AV1/FFmpeg AARs. The tests load a local HTML page, decode a generated AV1 test pattern with dav1d, and decode a generated E-AC-3 tone with FFmpeg. The tests force software renderer preference to exercise native decoders; the production player is hardware-first. Do not use these tests to claim full app, hardware, provider sign-in or other-ABI compatibility.

Build the AARs with `../aligned-decoders/build.sh`. From this directory, copy its output and generate local test media:

```sh
mkdir -p app/libs app/src/main/assets
cp /path/to/aligned-decoder-output/media3-*.aar app/libs/
ffmpeg -f lavfi -i 'testsrc2=size=160x90:rate=8' -t 1.5 -c:v libaom-av1 -cpu-used 8 -crf 40 -an -y app/src/main/assets/test-av1.mp4
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 1.5 -c:a eac3 -b:a 128k -y app/src/main/assets/test-eac3.mp4
gradle :app:connectedDebugAndroidTest
```

Requires Java 17+, Gradle 9.3.1, Android compile SDK 37.2 and an x86_64 API 26+ device/emulator. A separate 1.11.1 Gradle dependency report must be reviewed whenever dependencies change. An x86_64 API 28 software emulator passed all three tests; other ABIs were built but not runtime-tested.


`PopupProbeTest` additionally verifies callback ordering for one user-activated `about:blank` popup in a standalone Gecko session. An API 28 x86_64 software emulator passed this case. The test uses touchscreen input with a shell-input fallback and allows time for slow software composition. This does not test the Hi Browser host, other popup forms, real websites or authentication.
