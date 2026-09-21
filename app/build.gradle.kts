plugins {
    alias(libs.plugins.hugmun.android.application)
    alias(libs.plugins.hugmun.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.hugmun"

    defaultConfig {
        applicationId = "com.hugmun"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // Generated from res/resources.properties, which declares Russian as the locale
        // the unqualified values/ folder is written in.
        generateLocaleConfig = true
        localeFilters += setOf("ru")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    implementation(projects.engine.psychophysics)
    implementation(projects.engine.scheduling)
    implementation(projects.engine.signal)
    implementation(projects.engine.audio)
    implementation(projects.engine.visuals)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.home)
    implementation(projects.feature.vigilance)
    implementation(projects.feature.breathing)
    implementation(projects.feature.anchor)
    implementation(projects.feature.rhythm)
    implementation(projects.feature.assessment)
    implementation(projects.feature.insights)
    implementation(projects.feature.library)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    baselineProfile(projects.benchmark)

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

baselineProfile {
    automaticGenerationDuringBuild = false
}
