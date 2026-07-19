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
        versionName = BuildConfig.VERSION_NAME
        versionCode = BuildConfig.VERSION_CODE
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

// ======== Rust JNI 库 - 自动编译 + 复制（兼容配置缓存）=======

// 全部在配置阶段解析，只捕获 Serializable 值
val rustAbiMap =
    mapOf(
        "aarch64-linux-android" to "arm64-v8a",
        "armv7-linux-androideabi" to "armeabi-v7a",
        "x86_64-linux-android" to "x86_64",
    )
val rustProjectDir = rootProject.projectDir.resolve("rust/app_android")
val rustTargetDir = rustProjectDir.resolve("target")
val rustMode = if (!gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) "debug" else "release"
val rustJniOutput =
    layout.buildDirectory
        .dir("rust-jni")
        .get()
        .asFile.absolutePath

// ======== 编译任务 ========
rustAbiMap.forEach { (triple, abi) ->
    val abiId = abi.replace("-", "").replaceFirstChar { it.uppercase() }
    val buildTaskName = "buildRustLibs$abiId"

    tasks.register(buildTaskName, Exec::class.java) {
        description = "编译 Rust $triple (bandkit-app-android)"
        group = "BandKit"
        workingDir = rustProjectDir
        commandLine(
            "cargo",
            "build",
            "--target",
            triple,
            "-p",
            "bandkit-app-android",
            *if (rustMode == "release") arrayOf("--release") else emptyArray(),
        )
        // 跟踪源文件变化，确保 Rust 代码变更时自动重编
        inputs.dir(rustProjectDir.resolve("src"))
        inputs.file(rustProjectDir.resolve("Cargo.toml"))
        inputs.file(rustProjectDir.resolve("Cargo.lock"))
        // 输出目录声明，用于增量构建判断
        outputs.dir(rustTargetDir.resolve("$triple/$rustMode"))
    }
}

// 总编译任务
val rustBuildTasks =
    rustAbiMap.keys.map { triple ->
        val abi = rustAbiMap[triple]!!
        val abiId = abi.replace("-", "").replaceFirstChar { it.uppercase() }
        "buildRustLibs$abiId"
    }
tasks.register("buildRustLibs") {
    description = "编译所有 Rust 目标"
    group = "BandKit"
    dependsOn(rustBuildTasks)
}

// ======== 复制任务 ========
rustAbiMap.forEach { (triple, abi) ->
    val abiId = abi.replace("-", "").replaceFirstChar { it.uppercase() }
    val copyTaskName = "copyRustLibs$abiId"
    val buildTaskName = "buildRustLibs$abiId"
    val srcDir = rustTargetDir.resolve("$triple/$rustMode").absolutePath
    val dstDir = "$rustJniOutput/$abi"

    tasks.register(copyTaskName, Copy::class.java) {
        description = "复制 $triple/libbandkit_app_android.so 到 build/rust-jni/$abi/"
        group = "BandKit"
        dependsOn(buildTaskName)
        from(srcDir) {
            include("libbandkit_app_android.so")
        }
        into(dstDir)
    }
}

// 总复制任务
val rustCopyTasks =
    rustAbiMap.values.map { abi ->
        val abiId = abi.replace("-", "").replaceFirstChar { it.uppercase() }
        "copyRustLibs$abiId"
    }
tasks.register("copyRustLibs") {
    description = "编译 + 复制 Rust .so 到 build/rust-jni/"
    group = "BandKit"
    dependsOn(rustCopyTasks)
}

// 绑定到 Android 构建生命周期
val jniMergeTasks =
    mutableListOf<String>().apply {
        // debug 和 release 的 JNI 合并任务
        listOf("debug", "release").forEach { variant ->
            add("merge${variant.replaceFirstChar { it.uppercase() }}JniLibFolders")
            add("merge${variant.replaceFirstChar { it.uppercase() }}NativeLibs")
        }
    }
tasks
    .matching { it.name in jniMergeTasks }
    .configureEach { dependsOn("copyRustLibs") }

// 注册 jniLibs 源目录
afterEvaluate {
    val jniDir =
        layout.buildDirectory
            .dir("rust-jni")
            .get()
            .asFile
    android.sourceSets
        .getByName("debug")
        .jniLibs
        .srcDirs(jniDir)
    android.sourceSets
        .getByName("release")
        .jniLibs
        .srcDirs(jniDir)
}
