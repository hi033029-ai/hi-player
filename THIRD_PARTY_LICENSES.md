# Open Source Software & Third-Party Licenses

Hi Player utilizes the following open-source libraries and components. We gratefully acknowledge the contributions of the open-source community.

---

### 1. Android Media3 / ExoPlayer
- **Provider**: Google LLC / The Android Open Source Project
- **License**: Apache License 2.0
- **URL**: https://github.com/androidx/media
- **Modules**: `media3-exoplayer`, `media3-ui`, `media3-session`, `media3-common`, `media3-extractor`
- **Purpose**: Low-latency hardware-accelerated 4K/8K video playback, HDR10+, Dolby Vision/TrueHD/DTS audio decoding, subtitle rendering, and background playback service.

---

### 2. Jetpack Compose & AndroidX Libraries
- **Provider**: Google LLC / The Android Open Source Project
- **License**: Apache License 2.0
- **URL**: https://developer.android.com/jetpack
- **Modules**:
  - `androidx.compose.ui`
  - `androidx.compose.material3`
  - `androidx.compose.material-icons-extended`
  - `androidx.lifecycle:lifecycle-runtime-compose`
  - `androidx.lifecycle:lifecycle-viewmodel-compose`
  - `androidx.activity:activity-compose`
  - `androidx.navigation:navigation-compose`
  - `androidx.core:core-ktx`
- **Purpose**: Declarative UI layout, reactive architecture, theme management, and system integration.

---

### 3. Room Database
- **Provider**: Google LLC / The Android Open Source Project
- **License**: Apache License 2.0
- **URL**: https://developer.android.com/training/data-storage/room
- **Modules**: `androidx.room:room-runtime`, `androidx.room:room-ktx`
- **Purpose**: High-speed local SQLite persistence for video watch history, bookmarks, equalizer presets, playlists, and file caches.

---

### 4. Kotlin & Kotlinx Coroutines
- **Provider**: JetBrains s.r.o. / Google LLC
- **License**: Apache License 2.0
- **URL**: https://github.com/Kotlin/kotlinx.coroutines
- **Modules**: `kotlinx-coroutines-android`, `kotlinx-coroutines-core`
- **Purpose**: Asynchronous programming, media scanning, directory indexing, and background task execution.

---

### 5. Coil (Coroutine Image Loader)
- **Provider**: Coil Contributors
- **License**: Apache License 2.0
- **URL**: https://github.com/coil-kt/coil
- **Modules**: `io.coil-kt:coil-compose`
- **Purpose**: High-performance image and video frame thumbnail caching and loading.

---

### 6. Retrofit & OkHttp
- **Provider**: Square, Inc.
- **License**: Apache License 2.0
- **URL**: https://square.github.io/retrofit/ / https://square.github.io/okhttp/
- **Modules**: `com.squareup.retrofit2:retrofit`, `com.squareup.okhttp3:okhttp`
- **Purpose**: Robust HTTP networking and subtitle/metadata retrieval.

---

### 7. Moshi
- **Provider**: Square, Inc.
- **License**: Apache License 2.0
- **URL**: https://github.com/square/moshi
- **Modules**: `com.squareup.moshi:moshi-kotlin`
- **Purpose**: Lightweight JSON serialization and parsing.

---

### 8. Google Play Services & Credentials
- **Provider**: Google LLC
- **License**: Android Software Development Kit License / Apache 2.0
- **URL**: https://developers.google.com/android/guides/overview
- **Modules**: `androidx.credentials`, `com.google.android.libraries.identity.googleid`
- **Purpose**: Secure credential management and identity support.

---

### 9. Robolectric & JUnit (Testing Toolchain)
- **Provider**: Robolectric Team / JUnit Team
- **License**: MIT License (Robolectric) / Eclipse Public License 1.0 (JUnit)
- **URL**: https://github.com/robolectric/robolectric / https://junit.org/
- **Purpose**: Local JVM unit and integration testing without requiring an emulator.
