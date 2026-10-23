plugins {
    alias(libs.plugins.downtify.android.library)
    alias(libs.plugins.downtify.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.henriquesebastiao.downtify.core.data"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
