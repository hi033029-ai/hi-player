import com.android.build.api.dsl.ApplicationExtension

val android = extensions.getByType<ApplicationExtension>()
val generated = rootProject.layout.buildDirectory.dir("generated/gecko")
android.defaultConfig.versionCode = 99
android.defaultConfig.versionName = "1.0.98"
val geckoViewArtifactAbi = providers.gradleProperty("HI_PLAYER_GECKOVIEW_ABI").getOrElse("arm64-v8a")
val geckoArtifact = when (geckoViewArtifactAbi) {
    "universal" -> "geckoview"
    "arm64-v8a", "armeabi-v7a", "x86_64" -> "geckoview-$geckoViewArtifactAbi"
    else -> error("Unsupported GeckoView ABI artifact: $geckoViewArtifactAbi")
}
val requestedAbis = providers.gradleProperty("HI_PLAYER_ABI_FILTERS").getOrElse("arm64-v8a")
    .split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
if (geckoViewArtifactAbi == "universal") {
    require(requestedAbis.isNotEmpty() && requestedAbis.all { it in setOf("arm64-v8a", "armeabi-v7a") }) {
        "The universal GeckoView artifact may only be used for arm64-v8a and armeabi-v7a builds"
    }
} else {
    require(requestedAbis == listOf(geckoViewArtifactAbi)) {
        "The GeckoView artifact ABI ($geckoViewArtifactAbi) must match HI_PLAYER_ABI_FILTERS"
    }
}
android.sourceSets.getByName("main").apply {
    val directory = generated.get().dir("main").asFile.absolutePath
    java.directories.clear(); java.directories.add(directory)
    kotlin.directories.clear(); kotlin.directories.add(directory)
    assets.directories.add(rootProject.file("experiments/gecko-browser/assets").absolutePath)
    assets.directories.add(generated.get().dir("assets").asFile.absolutePath)
    manifest.srcFile(generated.map { it.file("AndroidManifest.xml") })
}
android.sourceSets.getByName("test").apply {
    val directory = generated.get().dir("test").asFile.absolutePath
    java.directories.clear(); java.directories.add(directory)
    kotlin.directories.clear(); kotlin.directories.add(directory)
}
val deviceTests = providers.gradleProperty("HI_PLAYER_GECKO_DEVICE_TEST").orNull == "true"
if (deviceTests) {
    android.sourceSets.getByName("debug").apply {
        java.directories.add(rootProject.file("experiments/gecko-browser/debug").absolutePath)
        kotlin.directories.add(rootProject.file("experiments/gecko-browser/debug").absolutePath)
        manifest.srcFile(rootProject.file("experiments/gecko-browser/debug/AndroidManifest.xml"))
    }
    android.sourceSets.getByName("androidTest").apply {
        java.directories.add(rootProject.file("experiments/gecko-browser/androidTest").absolutePath)
        kotlin.directories.add(rootProject.file("experiments/gecko-browser/androidTest").absolutePath)
    }
    android.buildTypes.getByName("debug").isDebuggable = true
}
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.media3") { useVersion("1.11.1"); because("GeckoView 159 requires matching Media3 Java and native decoder interfaces") }
    }
}
dependencies.add("implementation", "org.mozilla.geckoview:$geckoArtifact:159.0.20261102144711")
val prepareGeckoDecoders = tasks.register<Exec>("prepareGeckoDecoders") {
    workingDir(rootProject.projectDir)
    val output = rootProject.layout.buildDirectory.dir("gecko-decoders").get().asFile
    environment("OUT_DIR", output.absolutePath)
    environment("GRADLE_BIN", gradle.gradleHomeDir!!.resolve("bin/gradle").absolutePath)
    commandLine("bash", "experiments/gecko-browser/aligned-decoders/build.sh")
    inputs.files(rootProject.fileTree("experiments/gecko-browser/aligned-decoders") { include("**/*.kts", "build.sh") })
    outputs.files(output.resolve("media3-av1-1.11.1.aar"), output.resolve("media3-ffmpeg-1.11.1.aar"))
    onlyIf { !output.resolve("media3-av1-1.11.1.aar").isFile || !output.resolve("media3-ffmpeg-1.11.1.aar").isFile }
}
