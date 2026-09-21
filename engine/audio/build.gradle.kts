plugins {
    alias(libs.plugins.hugmun.android.library)
}

android {
    namespace = "com.hugmun.engine.audio"
}

dependencies {
    implementation(projects.engine.psychophysics)
}
