// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.rustAndroid)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

cargo {
    module = "../rust/corelib-standalone"
    libname = "corelib_standalone"
    targets = listOf("arm64", "arm", "x86_64")
    profile = "release"
    features {
        noDefaultBut("android-jni")
    }
}

val rustJniLibsDir = layout.buildDirectory.dir("rustJniLibs/android")

tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
    dependsOn("cargoBuild")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity)
    debugImplementation(compose.uiTooling)
}

android {
    ndkVersion = "30.0.14904198"
    buildToolsVersion = BuildConfig.BUILD_TOOLS_VERSION
    compileSdk {
        version =
            release(BuildConfig.COMPILE_SDK) {
                minorApiLevel = BuildConfig.COMPILE_SDK_MINOR
            }
    }
    defaultConfig {
        applicationId = BuildConfig.APPLICATION_ID
        minSdk = BuildConfig.MIN_SDK
        targetSdk = BuildConfig.TARGET_SDK
        versionName = "1.0.0"
        versionCode = 1
    }
    namespace = BuildConfig.APPLICATION_ID
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
