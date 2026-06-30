plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.thoughtvault"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.thoughtvault"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.currentTimeMillis() / 1000 / 60).toInt()
        versionName = "1.1.${System.currentTimeMillis() / 86400}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Security
    implementation(libs.security.crypto)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Timber
    implementation(libs.timber)

    // WorkManager
    implementation(libs.workmanager)

    // MQTT (Eclipse Paho — 原生 MQTT，不依赖 GMS)
    implementation(libs.paho.mqtt)
}

// APK 输出到统一目录，替换为你的实际路径
afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy("copyApk")
    }
}

tasks.register<Copy>("copyApk") {
    from("build/outputs/apk/release")
    include("*.apk")
    into("<your-apk-output-dir>")
}
