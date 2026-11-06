# Rebuild aligned Media3 1.11.1 decoders

This isolated build produces the official Media3 1.11.1 AV1 (dav1d) and FFmpeg extensions for arm64-v8a, armeabi-v7a, x86 and x86_64. It does **not** replace the shipping app libraries. The build preserves the previous FFmpeg decoder set: TrueHD, E-AC-3, AC-3, DTS, MLP, FLAC, Vorbis and Opus.

Requirements: Linux x86_64, JDK 17+, Gradle 9.3.1, Android SDK platform 36.1, CMake 3.22.1, NDK 27.2.12479018, git, Python 3, Meson, Ninja and NASM. Export `ANDROID_HOME` and `JAVA_HOME`, and ensure `gradle`, `meson`, `ninja` and `nasm` are on `PATH`. From any directory:

```sh
OUT_DIR=/path/to/output bash /path/to/aligned-decoders/build.sh
```

The script pins the Media3 1.11.1 source commit and its dav1d, cpu_features and FFmpeg source revisions, builds in a fresh temporary project, verifies each AAR contains exactly the four expected native libraries, and writes new AARs to `OUT_DIR`, not `app/libs`. For integration, every resolved `androidx.media3` artifact and both extensions must match; do not mix these AARs with the existing 1.5.1 player libraries. Review third-party licenses and redistribution obligations before shipping.

The matching libraries were built for all four ABIs. A generated AV1 clip and E-AC-3 tone decoded successfully on an API 28 x86_64 software emulator using the isolated `../aligned-probe`; no other ABI, production-app playback, or real device has been validated. The old Libgav1 bridge is incompatible and must not be used.
