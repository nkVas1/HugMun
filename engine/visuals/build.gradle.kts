plugins {
    alias(libs.plugins.hugmun.android.library)
    alias(libs.plugins.hugmun.android.compose)
}

android {
    namespace = "com.hugmun.engine.visuals"
}

dependencies {
    api(projects.engine.psychophysics)
    implementation(libs.androidx.core.ktx)
}
