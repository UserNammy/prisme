import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// TMDB bearer token is read from local.properties (git-ignored). If missing, the TMDB
// integration silently disables itself — the rest of the app keeps working.
val localProps: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}
val tmdbBearerToken: String = localProps.getProperty("TMDB_BEARER_TOKEN", "")

android {
    namespace = "fr.prisme.player"
    compileSdk = 34

    defaultConfig {
        // Kotlin package and applicationId are both fr.prisme.player.
        // Keeping the new ID from day one means installs and future signing belong to Prism.
        applicationId = "fr.prisme.player"
        minSdk = 29
        targetSdk = 34
        // CI passes VERSION_CODE (GitHub run number) so every published build is newer
        // than the last one; the in-app updater compares against it.
        versionCode = (System.getenv("VERSION_CODE") ?: localProps.getProperty("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"

        buildConfigField("String", "TMDB_BEARER_TOKEN", "\"$tmdbBearerToken\"")
        // GitHub "owner/repo" whose releases the in-app updater follows. Empty disables it.
        buildConfigField("String", "UPDATE_REPO", "\"${localProps.getProperty("UPDATE_REPO", "UserNammy/prisme")}\"")
    }

    signingConfigs {
        // Release keystore from local.properties; without it release builds fall back to the
        // debug key (fine for sideloading, not for distribution).
        val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE")
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 + resource shrinking shave significant size off the APK and remove a chunk
            // of dead code that would otherwise slow class loading on low-end TV boxes. Rules
            // for serialization, Retrofit, Room, Media3, etc. live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.tv.foundation.ExperimentalTvFoundationApi",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=" +
                project.file("compose-stability.conf").absolutePath,
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)

    implementation(libs.work.runtime.ktx)

    implementation(libs.youtube.player.core)
    implementation(libs.zxing.core)
    // Applies the baseline profiles shipped by Compose/Media3 (and ours, later) on sideloaded
    // installs, which never get Play cloud profiles: faster start and scrolling on TV boxes.
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
