plugins { id("com.android.application") }
android {
    namespace = "com.example.geckoprobe"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { applicationId = "com.example.geckoprobe"; minSdk = 26; targetSdk = 35; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("org.mozilla.geckoview:geckoview-x86_64:159.0.20261102144711")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation(files("libs/media3-av1-decoder-release.aar"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
