plugins {
    alias(libs.plugins.hugmun.android.library)
}

android {
    namespace = "com.hugmun.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.engine.psychophysics)
    implementation(projects.engine.scheduling)
    implementation(libs.kotlinx.datetime)
}
