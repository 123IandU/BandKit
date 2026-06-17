import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

kotlin {
    android {
        androidResources.enable = true
        buildToolsVersion = BuildConfig.BUILD_TOOLS_VERSION
        compileSdk {
            version = release(BuildConfig.COMPILE_SDK) {
                minorApiLevel = BuildConfig.COMPILE_SDK_MINOR
            }
        }
        minSdk = BuildConfig.MIN_SDK
        namespace = "com.miband.app.shared"
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.2")
            implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.2")
            implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.2")
        }
        androidMain.dependencies {
            // Android-specific dependencies
        }
        val desktopMain by getting
        desktopMain.dependencies {
            // Desktop-specific dependencies
        }
    }
}
