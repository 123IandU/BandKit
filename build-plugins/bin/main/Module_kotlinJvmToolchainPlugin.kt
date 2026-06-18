/**
 * Precompiled [module.kotlin-jvm-toolchain.gradle.kts][Module_kotlin_jvm_toolchain_gradle] script plugin.
 *
 * @see Module_kotlin_jvm_toolchain_gradle
 */
public
class Module_kotlinJvmToolchainPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Module_kotlin_jvm_toolchain_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
