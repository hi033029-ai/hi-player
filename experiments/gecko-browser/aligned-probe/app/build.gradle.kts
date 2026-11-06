plugins { id("com.android.application") }
android {
    namespace = "com.example.geckoprobe"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "com.example.geckoprobe"; minSdk = 26; targetSdk = 35; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    // Strict constraints make version drift fail resolution instead of silently
    // mixing artifacts. Local native AARs must also be built from 1.11.1.
    constraints {
        listOf("media3-common", "media3-container", "media3-database", "media3-datasource",
            "media3-decoder", "media3-exoplayer", "media3-exoplayer-hls", "media3-extractor")
            .forEach { module ->
                implementation("androidx.media3:$module") { version { strictly("1.11.1") } }
            }
    }
    implementation("org.mozilla.geckoview:geckoview-x86_64:159.0.20261102144711")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation(files("libs/media3-av1-1.11.1.aar", "libs/media3-ffmpeg-1.11.1.aar"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
