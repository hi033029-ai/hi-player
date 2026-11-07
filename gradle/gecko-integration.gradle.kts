import com.android.build.api.dsl.ApplicationExtension

val android = extensions.getByType<ApplicationExtension>()
val generated = rootProject.layout.buildDirectory.dir("generated/gecko")
// AGP 9 exposes directories, not source-file exclude filters.
// The generator retains all application sources, replacing only the entry
// point and compatibility-sensitive classes with context-checked versions.
android.sourceSets.getByName("main").apply {
    val directory = generated.get().dir("main").asFile.absolutePath
    java.directories.clear()
    java.directories.add(directory)
    kotlin.directories.clear()
    kotlin.directories.add(directory)
    manifest.srcFile(generated.map { it.file("AndroidManifest.xml") })
}
android.sourceSets.getByName("test").apply {
    val directory = generated.get().dir("test").asFile.absolutePath
    java.directories.clear()
    java.directories.add(directory)
    kotlin.directories.clear()
    kotlin.directories.add(directory)
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.media3") {
            useVersion("1.11.1")
            because("GeckoView 159 requires matching Media3 Java and native decoder interfaces")
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
