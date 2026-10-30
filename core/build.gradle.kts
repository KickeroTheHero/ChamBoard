plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.detekt)
}

// Pure-Kotlin engine: no Android, no network, no dependencies. Everything that
// decides *what* gets emitted and *when* lives here so it can be unit-tested
// on a plain JVM and audited in isolation.
// Target Java 17 bytecode but build on whatever JDK >= 17 is installed, so
// offline environments and CI don't need a toolchain download.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

// INV-2: the only runtime dependency allowed is the Kotlin standard library.
val verifyNoRuntimeDeps by tasks.registering {
    val runtime = configurations.named("runtimeClasspath")
    doLast {
        val extra = runtime.get().resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }
            .filterNot { it.group == "org.jetbrains.kotlin" || it.group == "org.jetbrains" }
        check(extra.isEmpty()) { "core must have no runtime dependencies (INV-2), found: $extra" }
    }
}
tasks.named("check") { dependsOn(verifyNoRuntimeDeps) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        // Plain-text report is what scripts/check summarises; skip the rest.
        txt.required.set(true)
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
