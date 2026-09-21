plugins {
    alias(libs.plugins.hugmun.android.library)
}

android {
    namespace = "com.hugmun.core.notifications"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)
}
