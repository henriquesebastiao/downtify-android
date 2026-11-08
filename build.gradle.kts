import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

val ktlintVersion = libs.versions.ktlint.get()

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/main/kotlin/**", "**/src/test/kotlin/**", "**/src/androidTest/kotlin/**")
            exclude("**/build/**", "build-logic/**")
        },
    )
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        sarif.required.set(false)
        txt.required.set(false)
        xml.required.set(false)
    }
}
