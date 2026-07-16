// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    id("module.kotlin-jvm-toolchain")
    id("module.spotless")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity)
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
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
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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

// ======== Rust JNI 库自动编译 ========
val rustProjectDir = rootProject.projectDir.resolve("rust/app_android")

// 任务阶段初始化 git submodule，不阻塞配置缓存
tasks.register<Exec>("initRustSubmodule") {
    description = "Initialize git submodule for app_android if not cloned"
    workingDir = rootProject.projectDir
    commandLine("git", "submodule", "update", "--init", "--recursive")
    // 如果 submodule 已存在，git 什么都不做，不报错
    isIgnoreExitValue = true
}

tasks.register<Exec>("buildRustLib") {
    dependsOn("initRustSubmodule")
    description = "Compile Rust JNI .so library via cargo"
    workingDir = rustProjectDir
    commandLine("cargo", "build", "--target", "aarch64-linux-android", "--release")
}

tasks.register<Copy>("copyRustLib") {
    dependsOn("buildRustLib")
    description = "Copy compiled .so to build/rust-jni"
    from(rustProjectDir.resolve("target/aarch64-linux-android/release/libastrobox_app_android.so"))
    into(layout.buildDirectory.dir("rust-jni").map { it.dir("arm64-v8a") })
}

// 注册 build/rust-jni 为 jniLibs 源目录
afterEvaluate {
    android.sourceSets.getByName("release").jniLibs.srcDirs(layout.buildDirectory.dir("rust-jni").get().asFile)
    android.sourceSets.getByName("debug").jniLibs.srcDirs(layout.buildDirectory.dir("rust-jni").get().asFile)
}

// 确保 copyRustLib 在 mergeJniLibFolders 之前运行
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyRustLib")
}
