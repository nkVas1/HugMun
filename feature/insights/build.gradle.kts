plugins {
    alias(libs.plugins.hugmun.android.feature)
}

android {
    namespace = "com.hugmun.feature.insights"
}

dependencies {
    implementation(projects.engine.scheduling)
}
