import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Public OAuth identifiers only; google-services.json supplies the real client by default.
val googleWebClientId = providers.gradleProperty("HI_PLAYER_GOOGLE_WEB_CLIENT_ID")
  .orElse(providers.environmentVariable("HI_PLAYER_GOOGLE_WEB_CLIENT_ID"))
  .orNull.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
val googleDriveEnabled = providers.gradleProperty("HI_PLAYER_DRIVE_ENABLED")
  .orElse(providers.environmentVariable("HI_PLAYER_DRIVE_ENABLED"))
  .map { it.equals("true", ignoreCase = true) }.getOrElse(false)
val oneDriveClientId = providers.gradleProperty("HI_PLAYER_ONEDRIVE_CLIENT_ID")
  .orElse(providers.environmentVariable("HI_PLAYER_ONEDRIVE_CLIENT_ID")).orNull.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
val dropboxAppKey = providers.gradleProperty("HI_PLAYER_DROPBOX_APP_KEY")
  .orElse(providers.environmentVariable("HI_PLAYER_DROPBOX_APP_KEY")).orNull.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
val packagedAbis = providers.gradleProperty("HI_PLAYER_ABI_FILTERS").orNull
  ?.split(',')
  ?.map { it.trim() }
  ?.filter { it.isNotEmpty() }
  ?.distinct()
  ?: listOf("arm64-v8a")
require(packagedAbis.isNotEmpty()) { "HI_PLAYER_ABI_FILTERS must contain at least one ABI" }
val geckoEnabled = providers.gradleProperty("HI_PLAYER_GECKO_ENABLED").getOrElse("true") == "true"
val selectedAppGeckoAbi = providers.gradleProperty("HI_PLAYER_GECKOVIEW_ABI").getOrElse("arm64-v8a")
if (geckoEnabled && selectedAppGeckoAbi != "universal") {
  require(packagedAbis == listOf(selectedAppGeckoAbi)) {
    "A single-ABI GeckoView artifact ($selectedAppGeckoAbi) must be paired with the same ABI filter"
  }
}
val releaseBuildRequested = gradle.startParameter.taskNames.any {
  it.substringAfterLast(':').contains("Release", ignoreCase = true)
}
if (releaseBuildRequested) {
  require(packagedAbis.all { it in setOf("arm64-v8a", "armeabi-v7a") }) {
    "Release builds may package arm64-v8a and armeabi-v7a only"
  }
}
val bundleBuildRequested = gradle.startParameter.taskNames.any {
  it.substringAfterLast(':').startsWith("bundle", ignoreCase = true)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  defaultConfig {
    applicationId = "com.aistudio.hiplayer.vdmk"
    minSdk = 24
    targetSdk = 36
    versionCode = 95
    versionName = "1.0.94"
    // AGP rejects NDK filters together with APK ABI split filters. Bundle tasks use
    // NDK filters; APK tasks are restricted by the split configuration below.
    if (bundleBuildRequested) ndk { abiFilters.addAll(packagedAbis) }
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    buildConfigField("boolean", "GOOGLE_DRIVE_ENABLED", googleDriveEnabled.toString())
    buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"$oneDriveClientId\"")
    buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  splits {
    abi {
      isEnable = !bundleBuildRequested
      if (!bundleBuildRequested) {
        reset()
        include(*packagedAbis.toTypedArray())
        isUniversalApk = false
      }
    }
  }
  bundle {
    abi { enableSplit = true }
  }
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }
  assetPacks += listOf(":webfilters")
  buildTypes {
    release {
      buildConfigField("boolean", "PLAY_STORE_BUILD", "true")
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      buildConfigField("boolean", "PLAY_STORE_BUILD", "false")
      signingConfig = signingConfigs.getByName("debugConfig")
      // Keep installable builds optimized; the device-test property alone enables debugging.
      isDebuggable = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo { includeInApk = false; includeInBundle = true }
}
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}
googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation("com.hierynomus:smbj:0.13.0")
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation("com.google.android.gms:play-services-auth:21.4.0")
  implementation("com.google.android.gms:play-services-cast-framework:22.3.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.mediarouter:mediarouter:1.8.0")
  implementation("androidx.fragment:fragment-ktx:1.8.6")
  implementation("androidx.browser:browser:1.8.0")
  implementation("androidx.work:work-runtime-ktx:2.10.1")
  implementation("com.google.android.play:asset-delivery:2.3.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  implementation(files("libs/media3-ffmpeg-decoder-debug.aar"))
  implementation(files("libs/media3-av1-decoder-release.aar"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.reorderable)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.core:core-splashscreen:1.0.1")
  implementation(libs.androidx.webkit)
  implementation(libs.play.services.code.scanner)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.extractor)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
