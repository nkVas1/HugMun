plugins {
    alias(libs.plugins.hugmun.android.feature)
}

android {
    namespace = "com.hugmun.feature.rhythm"
}

dependencies {
    implementation(projects.engine.audio)
    implementation(projects.engine.visuals)
    implementation(projects.engine.psychophysics)
}
