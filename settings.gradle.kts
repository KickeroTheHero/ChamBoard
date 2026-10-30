pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                // Only Google-Maven-exclusive groups; Guava/protobuf etc. come from Central,
                // so environments without access to dl.google.com don't need it for them.
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                includeGroupByRegex("com\\.google\\.prefab.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                // Only Google-Maven-exclusive groups; Guava/protobuf etc. come from Central,
                // so environments without access to dl.google.com don't need it for them.
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                includeGroupByRegex("com\\.google\\.prefab.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ChamBoard"
include(":core")
// `-PcoreOnly=true` builds and tests the pure-Kotlin engine without the Android
// toolchain (useful on machines without an SDK or Google Maven access).
if (providers.gradleProperty("coreOnly").orNull != "true") {
    include(":app")
} else if (providers.gradleProperty("androidJar").isPresent) {
    // Local stand-in for :app: compile + detekt app sources against android.jar.
    include(":appcheck")
    project(":appcheck").projectDir = file("tools/appcheck")
}
