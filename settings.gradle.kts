pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "downtify-android"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:network")
include(":core:data")
include(":core:player")
if (providers.gradleProperty("includeBenchmarks").orNull == "true") include(":macrobenchmark")
