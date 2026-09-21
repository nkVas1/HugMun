plugins {
    alias(libs.plugins.hugmun.android.library)
    alias(libs.plugins.hugmun.android.room)
}

android {
    namespace = "com.hugmun.core.database"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
}
