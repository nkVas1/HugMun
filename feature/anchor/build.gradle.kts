plugins {
    alias(libs.plugins.hugmun.android.feature)
}

android {
    namespace = "com.hugmun.feature.anchor"
}

dependencies {
    implementation(projects.engine.scheduling)
}
