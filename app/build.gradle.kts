import java.security.MessageDigest
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---- Stockfish NNUE nets: downloaded once into build/ (not committed, ~98 MB) ----
// The file name carries the first 12 hex digits of the SHA-256, which the download is checked against.
val nnueNets = listOf("nn-1a298aa575a0.nnue") // must match EvalFileDefaultName in Stockfish's evaluate.h
val nnueAssetsDir = layout.buildDirectory.dir("generated/nnue/assets")

val downloadNnue = tasks.register("downloadNnue") {
    group = "build"
    description = "Downloads the Stockfish NNUE nets into assets/nnue"
    val outDir = nnueAssetsDir.map { it.dir("nnue") }
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.also { it.mkdirs() }
        // Drop nets of an older Stockfish, so they do not end up in the APK.
        dir.listFiles { f -> f.name.endsWith(".nnue") && f.name !in nnueNets }?.forEach { it.delete() }
        for (name in nnueNets) {
            val target = File(dir, name)
            if (target.exists()) continue
            val tmp = File(dir, "$name.part")
            uri("https://tests.stockfishchess.org/api/nn/$name").toURL().openStream().use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(sha.startsWith(name.removePrefix("nn-").removeSuffix(".nnue"))) { "Checksum mismatch for $name" }
            check(tmp.renameTo(target)) { "Could not move $name into place" }
        }
    }
}

android {
    namespace = "com.checkmind.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.checkmind.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Phones (arm64) and the emulator (x86_64) only.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].assets.srcDir(nnueAssetsDir)
    androidResources {
        noCompress += "nnue" // loaded by copying to disk; compressing 75 MB again gains nothing
    }

    // Release signing: reads keystore.properties at the repo root (git-ignored).
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.configureEach {
    // Anything that reads the assets source dir must run after the nets are downloaded.
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) dependsOn(downloadNnue)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":chess-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
