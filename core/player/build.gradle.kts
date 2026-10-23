plugins {
    alias(libs.plugins.downtify.android.library)
    alias(libs.plugins.downtify.hilt)
}

android {
    namespace = "com.henriquesebastiao.downtify.core.player"
}

dependencies {
    api(projects.core.data)
    api(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.database)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
}
