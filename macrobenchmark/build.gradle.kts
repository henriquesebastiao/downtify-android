plugins {
    id("com.android.test")
}

android {
    namespace = "com.henriquesebastiao.downtify.benchmark"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.fullTracing.enable"] = "false"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("beta") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "beta" }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
