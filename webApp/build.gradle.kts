// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

// ======== Rust WASM 编译 ========
val rustWasmDir = rootProject.projectDir.resolve("rust/app_wasm")
val wasmOutputDir = layout.buildDirectory.dir("wasm-bindgen")

// 初始化 git submodule（与 Android 构建一致）
tasks.register<Exec>("initRustSubmodule") {
    description = "Update git submodule to latest remote commit"
    workingDir = rootProject.projectDir
    commandLine("git", "submodule", "update", "--init", "--remote", "--recursive")
    isIgnoreExitValue = true
}

// 编译 Rust WASM 库
tasks.register<Exec>("buildRustWasm") {
    dependsOn("initRustSubmodule")
    description = "Compile Rust WASM library via wasm-pack"
    workingDir = rustWasmDir
    commandLine(
        "wasm-pack",
        "build",
        "--target",
        "web",
        "--out-dir",
        wasmOutputDir.get().asFile.absolutePath,
        "--release",
    )
}

// 确保 wasm-pack 构建在 Webpack 之前完成
tasks.matching { it.name.contains("processResources") || it.name.contains("wasmJsDevelopmentRun") }.configureEach {
    dependsOn("buildRustWasm")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
        }
    }
}
