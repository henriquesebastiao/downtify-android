plugins {
    alias(libs.plugins.downtify.android.application)
    alias(libs.plugins.downtify.android.compose)
    alias(libs.plugins.downtify.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.henriquesebastiao.downtify"

    defaultConfig {
        applicationId = "com.henriquesebastiao.downtify"
        versionCode = 3
        versionName = "0.1.0-beta.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key comes from the environment (the GitHub Actions release workflow sets these;
    // see .github/workflows/release.yml). Without it — a local `assembleRelease` — the APK is signed
    // with the debug key: it installs, but can't be updated by a release signed with the real key.
    val releaseKeystore = providers.environmentVariable("DOWNTIFY_KEYSTORE_FILE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("DOWNTIFY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DOWNTIFY_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("DOWNTIFY_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.player)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.work.runtime)
    implementation(libs.androidx.palette)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.mlkit.barcode)
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
