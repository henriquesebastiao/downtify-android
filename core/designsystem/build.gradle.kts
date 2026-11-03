plugins {
    alias(libs.plugins.downtify.android.library)
    alias(libs.plugins.downtify.android.compose)
}

android {
    namespace = "com.henriquesebastiao.downtify.core.designsystem"
}

dependencies {
    implementation(projects.core.model)
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.androidx.palette)
    implementation(libs.material.color.utilities)
    implementation(libs.coil.compose)
}
