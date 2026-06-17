import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

plugins.withType<KotlinBasePlugin> {{
    extensions.configure<KotlinBaseExtension> {{
        jvmToolchain(BuildConfig.JDK_VERSION)
    }}
}}
