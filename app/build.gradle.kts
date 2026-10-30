import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Thin Android shell: no AndroidX, no libraries, all logic in :core.
android {
    namespace = "dev.chamboard.ime"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.chamboard"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Dependency-free runner that renders screens for CI screenshots.
        testInstrumentationRunner = "dev.chamboard.ime.ScreenshotInstrumentation"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // App code must not use BuildConfig or R so the local compile check (tools/appcheck) works.
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
}

// INV-1: the merged manifest of every variant must declare no permissions.
val permissionChecks = mutableListOf<TaskProvider<*>>()
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val name = variant.name.replaceFirstChar { it.uppercase() }
        permissionChecks += tasks.register("verifyNoPermissions$name") {
            inputs.file(manifest)
            doLast {
                // Parse the XML (not a text search) so comments can't trigger or hide a match.
                val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(manifest.get().asFile)
                val found = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
                    val nodes = doc.getElementsByTagName(tag)
                    (0 until nodes.length).map { i ->
                        nodes.item(i).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                            ?.nodeValue ?: tag
                    }
                }
                check(found.isEmpty()) { "INV-1: merged manifest declares permissions: $found" }
            }
        }
    }
}
tasks.register("verifyNoPermissions") {
    description = "INV-1: fail if any variant's merged manifest declares a permission."
    dependsOn(permissionChecks)
}
