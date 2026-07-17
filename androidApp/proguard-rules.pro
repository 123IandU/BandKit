# Copyright 2026, compose-miuix-ui contributors
# SPDX-License-Identifier: Apache-2.0

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Miuix
-keep class top.yukonga.miuix.kmp.** { *; }

# Keep JNI bridge
-keep class com.bandkit.app.core.NativeDevice { *; }
