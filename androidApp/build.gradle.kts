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

// ======== Bundletool: AAB → .apks ========
val bundletoolVersion = "1.18.1"
val bundletoolJar = rootProject.projectDir.resolve("tools/bundletool-all-$bundletoolVersion.jar")

tasks.register("downloadBundletool") {
    description = "Download bundletool JAR if not present"
    onlyIf { !bundletoolJar.exists() }
    doLast {
        bundletoolJar.parentFile.mkdirs()
        val destPath = bundletoolJar.absolutePath
        val url = "https://github.com/google/bundletool/releases/download/$bundletoolVersion/bundletool-all-$bundletoolVersion.jar"
        val pb = ProcessBuilder("curl.exe", "-L", "-o", destPath, url)
        pb.inheritIO()
        val exitCode = pb.start().waitFor()
        if (exitCode != 0) throw GradleException("Failed to download bundletool (exit code $exitCode)")
        logger.lifecycle("Downloaded bundletool to $destPath")
    }
}

tasks.register<JavaExec>("buildApks") {
    description = "Build .apks from AAB (run after bundleRelease)"
    dependsOn("downloadBundletool")
    dependsOn("bundleRelease")
    classpath(bundletoolJar)
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    args =
        mutableListOf(
            "build-apks",
            "--bundle=${layout.buildDirectory.get().asFile}/outputs/bundle/release/androidApp-release.aab",
            "--output=${layout.buildDirectory.get().asFile}/outputs/bundle/release/androidApp-release.apks",
            "--overwrite",
        )
}

tasks.register<JavaExec>("installApks") {
    description = "Install .apks to connected device via bundletool"
    dependsOn("buildApks")
    classpath(bundletoolJar)
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    args =
        mutableListOf(
            "install-apks",
            "--apks=${layout.buildDirectory.get().asFile}/outputs/bundle/release/androidApp-release.apks",
        )
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
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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

// ======== Rust JNI 库自动编译（多架构） ========
val rustProjectDir = rootProject.projectDir.resolve("rust/app_android")
val isRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val buildMode = if (isRelease) "release" else "debug"

// 架构映射：abi名称 -> Rust target
val rustTargets =
    mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "armv7-linux-androideabi",
        "x86_64" to "x86_64-linux-android",
    )

// NDK strip 工具路径（从环境变量获取）
val ndkHome = System.getenv("ANDROID_NDK_HOME") ?: "${System.getenv("ANDROID_HOME")}/ndk/${android.ndkVersion}"
val stripTool = file("$ndkHome/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip")

// 自动拉取 app_android submodule 到最新版本
tasks.register<Exec>("initRustSubmodule") {
    description = "Update git submodule to latest remote commit"
    workingDir = rootProject.projectDir
    // --remote: 更新到远程分支的最新 commit（而非父仓库记录的 commit）
    commandLine("git", "submodule", "update", "--init", "--remote", "--recursive")
    // 如果已是最新，git 什么都不做，不报错
    isIgnoreExitValue = true
}

// 为每种架构创建编译任务
rustTargets.forEach { (abi, rustTarget) ->
    val safeAbi = abi.replace("-", "_")
    val buildTaskName = "buildRustLib_$safeAbi"
    val copyTaskName = "copyRustLib_$safeAbi"
    val stripTaskName = "stripRustLib_$safeAbi"

    tasks.register<Exec>(buildTaskName) {
        dependsOn("initRustSubmodule")
        description = "Compile Rust JNI .so for $abi"
        workingDir = rustProjectDir
        val cargoArgs = mutableListOf("build", "--target", rustTarget)
        if (isRelease) cargoArgs.add("--release")
        commandLine("cargo", *cargoArgs.toTypedArray())
    }

    tasks.register<Copy>(copyTaskName) {
        dependsOn(buildTaskName)
        description = "Copy compiled .so for $abi to build/rust-jni"
        from(rustProjectDir.resolve("target/$rustTarget/$buildMode/libastrobox_app_android.so"))
        into(layout.buildDirectory.dir("rust-jni").map { it.dir(abi) })
    }

    // strip debug symbols（仅 release 构建）
    if (isRelease) {
        tasks.register<Exec>(stripTaskName) {
            dependsOn(copyTaskName)
            description = "Strip debug symbols from .so for $abi"
            val soFile =
                layout.buildDirectory
                    .file("rust-jni/$abi/libastrobox_app_android.so")
                    .get()
                    .asFile
            commandLine(stripTool.absolutePath, "--strip-all", soFile.absolutePath)
        }
    }
}

// 注册 build/rust-jni 为 jniLibs 源目录
afterEvaluate {
    android.sourceSets
        .getByName("release")
        .jniLibs
        .srcDirs(
            layout.buildDirectory
                .dir("rust-jni")
                .get()
                .asFile,
        )
    android.sourceSets
        .getByName("debug")
        .jniLibs
        .srcDirs(
            layout.buildDirectory
                .dir("rust-jni")
                .get()
                .asFile,
        )
}

// 确保 Rust .so 在 mergeJniLibFolders 之前准备好
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    if (isRelease) {
        dependsOn(tasks.matching { it.name.startsWith("stripRustLib_") })
    } else {
        dependsOn(tasks.matching { it.name.startsWith("copyRustLib_") })
    }
}
