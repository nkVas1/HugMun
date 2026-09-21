plugins {
    `kotlin-dsl`
}

group = "com.hugmun.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt())
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "hugmun.android.application"
            implementationClass = "com.hugmun.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "hugmun.android.library"
            implementationClass = "com.hugmun.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "hugmun.android.compose"
            implementationClass = "com.hugmun.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "hugmun.android.feature"
            implementationClass = "com.hugmun.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "hugmun.android.room"
            implementationClass = "com.hugmun.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "hugmun.jvm.library"
            implementationClass = "com.hugmun.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
