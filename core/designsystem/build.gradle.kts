plugins {
    alias(libs.plugins.hugmun.android.library)
    alias(libs.plugins.hugmun.android.compose)
}

android {
    namespace = "com.hugmun.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.core)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.core.ktx)
    implementation(projects.core.model)
}
