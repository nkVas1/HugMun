plugins {
    alias(libs.plugins.hugmun.android.library)
}

android {
    namespace = "com.hugmun.core.datastore"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
}
