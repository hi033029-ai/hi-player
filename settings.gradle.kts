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
      if (providers.gradleProperty("HI_PLAYER_GECKO_ENABLED").orNull == "true" && requested.version != null) {
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

// -PHI_PLAYER_GECKO_ENABLED=false retains the original legacy build.
if (providers.gradleProperty("HI_PLAYER_GECKO_ENABLED").orNull == "true") {
  val preparation = providers.exec {
    workingDir(settingsDir)
    commandLine("python3", "tools/prepare_gecko_overlay.py")
  }
  logger.lifecycle(preparation.standardOutput.asText.get())
  preparation.result.get().assertNormalExitValue()
  rootProject.buildFileName = "build/generated/gecko/root.gradle.kts"
  project(":app").buildFileName = "../build/generated/gecko/app.gradle.kts"
}
