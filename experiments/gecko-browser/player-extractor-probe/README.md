# MP4 extractor compatibility probe

This is a **test-only probe**, not a browser integration or production player upgrade. It generates original synthetic two-second AVC/AAC and HEVC/AAC MP4 files and exercises the app's own `OptimizedPlayerBuilder` extractor factory: it parses both streams, checks formats and samples, obtains a seek point and reads samples after a seek. It does not decode, render, play sound, test subtitles/HDR, exercise fragmented MP4 or auxiliary tracks, or cover actual user media.

## Reproduction

Work in a disposable copy of the feature branch, with Android SDK 36.1, JDK 21, Gradle 9.3.1 and `ffmpeg` built with libx264, libx265 and AAC. Do not modify the shipping build or commit generated video.

1. Run `bash generate-fixtures.sh <copy>/app/src/test/resources/mp4` and copy `Media3Mp4ExtractionTest.kt` to `<copy>/app/src/test/java/com/example/playback/`.
2. Add `testImplementation("androidx.media3:media3-test-utils:1.5.1")` to the copy's app dependencies. Run `gradle :app:testDebugUnitTest --tests '*Media3Mp4ExtractionTest'` for the 1.5.1 baseline.
3. Apply `../patches/media3-111-player-candidate.patch` to the copy, set the Media3 catalog version and test-utils version to 1.11.1, and replace both decoder AARs with the matched 1.11.1 builds produced by `../aligned-decoders/build.sh`. Run `gradle :app:testDebugUnitTest --tests '*Media3Mp4ExtractionTest' --tests '*OptimizedPlayerBuilderFlagsTest'`.
4. Inspect `:app:dependencies --configuration debugRuntimeClasspath` to confirm every resolved `androidx.media3` module is 1.11.1. This does not prove the JNI binaries are correctly built; the aligned decoder build separately verifies four-ABI packaging.

The baseline tests passed on 1.5.1 and the two MP4 tests plus flag-value test passed on 1.11.1 in a temporary app copy. The 1.11.1 runtime dependency report resolved all 41 reported Media3 occurrences to 1.11.1. These were Robolectric/JVM parsing tests; device playback remains unverified. Upstream Media3 1.5.1 uses bit 6 for editable tracks, whereas 1.11.1 uses it for auxiliary tracks. Both can select a non-primary track if its respective metadata exists. The probe's synthetic MP4s contain neither form, so success does not resolve that semantic change and the player candidate remains unapplied.
