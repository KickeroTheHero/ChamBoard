// Local-only compile + detekt check for app sources.
// Compiles app/src/main/kotlin as plain Kotlin/JVM against a mirrored
// android.jar, so environments without the Android toolchain get fast
// feedback. The real APK, Android Lint and manifest checks run in CI.
// App code must not reference generated R/BuildConfig for this to work.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.detekt)
}

val androidJar: String = providers.gradleProperty("androidJar").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    sourceSets.named("main") {
        kotlin.srcDir(rootProject.file("app/src/main/kotlin"))
        kotlin.srcDir(rootProject.file("app/src/androidTest/kotlin"))
    }
}

dependencies {
    compileOnly(files(androidJar))
    implementation(project(":core"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom(rootProject.file("app/src/main/kotlin"), rootProject.file("app/src/androidTest/kotlin"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        txt.required.set(true)
        html.required.set(false)
        xml.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
