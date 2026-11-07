pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
  resolutionStrategy {
    eachPlugin {
      if (providers.gradleProperty("HI_PLAYER_GECKO_ENABLED").orNull == "true") {
        when (requested.id.id) {
          "org.jetbrains.kotlin.plugin.compose" -> useVersion("2.4.20")
          "com.google.devtools.ksp" -> useVersion("2.3.13")
        }
      }
    }
  }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://maven.mozilla.org/maven2/") }
  }
}
rootProject.name = "Hi Player"
include(":app", ":webfilters")

// Explicit opt-in until migration and device validation gates are green.
// -PHI_PLAYER_GECKO_ENABLED=false always selects the unchanged legacy build.
if (providers.gradleProperty("HI_PLAYER_GECKO_ENABLED").orNull == "true") {
  val preparation = ProcessBuilder("python3", "tools/prepare_gecko_build.py")
    .directory(settingsDir).inheritIO().start()
  check(preparation.waitFor() == 0) { "Gecko source preparation failed; original sources were not modified." }
  rootProject.buildFileName = "build/generated/gecko/root.gradle.kts"
  project(":app").buildFileName = "../build/generated/gecko/app.gradle.kts"
}
