plugins {
    alias(libs.plugins.hugmun.android.feature)
}

android {
    namespace = "com.hugmun.feature.vigilance"
}

dependencies {
    implementation(projects.engine.psychophysics)
    implementation(projects.engine.visuals)
}
