import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.subedit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.subedit"
        minSdk = 26          // Android 8.0+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose (Material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3 (ExoPlayer) — decoding video via hardware MediaCodec + software FFmpeg fallback (AVC High 4:2:2)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.decoder.ffmpeg)

    // libmpv Android wrapper (libmpv native + libass 100% Aegisub parity + FFmpeg software decoding)
    implementation(libs.mpv.android.lib)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// Fallback resolusi jika modul native FFmpeg belum di-build lokal pada mesin pengembang
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        if (findProject(":lib-decoder-ffmpeg") == null && !file("libs/media3-decoder-ffmpeg.aar").exists()) {
            substitute(module("androidx.media3:media3-decoder-ffmpeg"))
                .using(module("androidx.media3:media3-decoder:${libs.versions.media3.get()}"))
                .because("Gunakan artefak media3-decoder jika binary native FFmpeg belum tersedia secara lokal")
        }
    }
}
