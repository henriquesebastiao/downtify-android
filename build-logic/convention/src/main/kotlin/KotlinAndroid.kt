import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal object Sdk {
    const val COMPILE = 37

    // compose-ui 1.13.0 asks for API 37.1: Android now publishes minor levels, so the major alone
    // resolves to android-37.0 and checkDebugAarMetadata refuses it.
    const val COMPILE_MINOR = 1
    const val TARGET = 37
    const val MIN = 26
}

internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = Sdk.COMPILE
        compileSdkMinor = Sdk.COMPILE_MINOR
        defaultConfig.minSdk = Sdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        lint.warningsAsErrors = false
        lint.abortOnError = true
        lint.checkDependencies = true
        // Lint 9.4 crashes analysing Robolectric Compose tests (K2 UAST); tests get detekt + ktlint.
        lint.ignoreTestSources = true
        lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    // Hilt generates test sources even in modules that have no unit tests yet.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xannotation-default-target=param-property",
            )
        }
    }
}
