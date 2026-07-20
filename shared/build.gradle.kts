// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

kotlin {
    android {
        androidResources.enable = true
        buildToolsVersion = BuildConfig.BUILD_TOOLS_VERSION
        compileSdk {
            version =
                release(BuildConfig.COMPILE_SDK) {
                    minorApiLevel = BuildConfig.COMPILE_SDK_MINOR
                }
        }
        minSdk = BuildConfig.MIN_SDK
        namespace = "com.bandkit.app.shared"
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.miuix.ui)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.preference)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation("scripta:editor")
        }
        val desktopMain by getting
        desktopMain.dependencies {
            // Desktop-specific dependencies
        }
    }
}
