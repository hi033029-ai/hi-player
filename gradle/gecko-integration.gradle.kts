import com.android.build.api.dsl.ApplicationExtension

val android = extensions.getByType<ApplicationExtension>()
val generated = rootProject.layout.buildDirectory.dir("generated/gecko")
android.sourceSets.getByName("main").apply {
    java.srcDir(rootProject.file("experiments/gecko-browser"))
    java.srcDir(rootProject.file("experiments/gecko-browser/integration"))
    java.srcDir(generated.map { it.dir("main") })
    java.exclude("tests/**", "integration/**", "integration-tests/**", "aligned-decoders/**", "aligned-probe/**", "runtime-probe/**", "player-extractor-probe/**")
    java.exclude("LegacyGav1RenderersFactory.kt", "GeckoFilePickerLauncher.kt", "GeckoRuntimePermissionLauncher.kt", "GeckoBrowserServices.kt", "GeckoTabController.kt", "GeckoManagedTab.kt", "GeckoTabEngine.kt", "GeckoBrowserChrome.kt")
    java.exclude("com/example/ui/screens/HiBrowserScreen.kt", "com/example/playback/OptimizedPlayerBuilder.kt", "com/example/player/SharpnessReductionEffect.kt")
    manifest.srcFile(generated.map { it.file("AndroidManifest.xml") })
}
android.sourceSets.getByName("test").apply {
    java.srcDir(rootProject.file("experiments/gecko-browser/tests"))
    java.srcDir(rootProject.file("experiments/gecko-browser/integration-tests"))
    java.srcDir(generated.map { it.dir("test") })
    java.exclude("LegacyGav1RegistrationTest.kt", "com/example/player/SoftwareDecoderRegistrationTest.kt")
}

// Gecko and all application player modules must use matching Java/JNI interfaces.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.media3") {
            useVersion("1.11.1")
            because("GeckoView 159 requires Media3 1.11.1 and matching rebuilt native decoders")
        }
    }
}
dependencies.add("implementation", "org.mozilla.geckoview:geckoview:159.0.20261102144711")

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
