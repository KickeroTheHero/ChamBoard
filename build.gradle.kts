// The Kotlin and Android Gradle plugins must share one classloader, so both go on
// the root classpath. The Android plugin is added only when :app is part of the
// build: `-PcoreOnly=true` never resolves Google's Maven, so the engine
// builds in offline or SDK-less environments.
buildscript {
    val coreOnly = gradle.startParameter.projectProperties["coreOnly"] == "true"
    repositories {
        if (!coreOnly) {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google\\.android.*")
                    includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                    includeGroupByRegex("com\\.google\\.prefab.*")
                    includeGroupByRegex("androidx.*")
                }
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.plugins.kotlin.jvm.map { "org.jetbrains.kotlin:kotlin-gradle-plugin:${it.version}" })
        if (!coreOnly) {
            classpath(
                libs.plugins.android.application.map {
                    "com.android.tools.build:gradle:${it.version}"
                },
            )
        }
    }
}

plugins {
    alias(libs.plugins.spotless)
}

// Spotless's incremental state went stale once (a violation passed locally and
// in CI, then failed on a clean runner). Formatting checks are cheap, so always
// run them rather than trust up-to-date checks or caches.
tasks.matching { it.name.startsWith("spotless") }.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    format("misc") {
        target("**/*.md", "**/*.yml", "**/*.sh", ".editorconfig", "scripts/*")
        targetExclude("**/build/**", ".gradle/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
