fn main() {
    // For Android cross-compilation, set the following environment variables:
    // ANDROID_NDK_HOME, CC_aarch64-linux-android, etc.
    // Use cargo-ndk or a custom build script for Android targets.
    println!("cargo:rerun-if-changed=src/");
}
