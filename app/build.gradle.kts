import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---- Opening book: games.pgn -> book.bin (generated into assets at build time) ----
val bookTool = configurations.create("bookTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val bookOutDir = layout.buildDirectory.dir("generated/book/assets")

val generateBook = tasks.register<JavaExec>("generateBook") {
    group = "build"
    description = "Compiles every .pgn in book-builder/data into assets/book.bin"
    val pgn = rootProject.layout.projectDirectory.dir("book-builder/data")
    val out = bookOutDir.map { it.file("book.bin") }
    classpath = bookTool
    mainClass.set("com.checkmind.book.BuildBookKt")
    jvmArgs("-Xmx3g")
    inputs.dir(pgn)
    outputs.file(out)
    argumentProviders.add(
        CommandLineArgumentProvider { listOf(pgn.asFile.absolutePath, out.get().asFile.absolutePath) },
    )
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

    sourceSets["main"].assets.srcDir(bookOutDir)

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.configureEach {
    // Anything that reads the assets source dir must run after the book is generated.
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) dependsOn(generateBook)
}

dependencies {
    add("bookTool", project(":book-builder"))
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
