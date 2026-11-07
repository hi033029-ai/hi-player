plugins { id("com.android.library") }
val sourceRoot = providers.environmentVariable("MEDIA3_SOURCE").get()
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 24
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        externalNativeBuild { cmake { arguments.add("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"); arguments.add("-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"); targets.add("ffmpegJNI") } }
    }
    externalNativeBuild { cmake { path = file("${sourceRoot}/libraries/decoder_ffmpeg/src/main/jni/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets { getByName("main") { manifest.srcFile("${sourceRoot}/libraries/decoder_ffmpeg/src/main/AndroidManifest.xml"); java.srcDir("${sourceRoot}/libraries/decoder_ffmpeg/src/main/java") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api("androidx.media3:media3-decoder:1.11.1")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.annotation:annotation:1.9.1")
    compileOnly("org.checkerframework:checker-qual:3.49.0")
}
