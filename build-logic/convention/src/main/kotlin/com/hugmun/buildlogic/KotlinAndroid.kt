/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Shared Android + Kotlin configuration.
 *
 * Two things surprise people reading this for the first time:
 *
 * 1. `org.jetbrains.kotlin.android` is never applied anywhere in this build. AGP 9 has
 *    built-in Kotlin support and applies the Kotlin plugin itself, which is why the
 *    [KotlinAndroidProjectExtension] below resolves without us asking for it.
 * 2. AGP 9's new DSL dropped the type parameters from [CommonExtension] and no longer
 *    exposes the `defaultConfig { }` / `lint { }` lambda overloads on the common
 *    interface — only on the concrete Application/Library extensions. Hence the
 *    property-access style here.
 */
internal fun Project.configureAndroidKotlin(extension: CommonExtension) {
    extension.compileSdk = catalogVersion("androidCompileSdk").toInt()
    extension.defaultConfig.minSdk = catalogVersion("androidMinSdk").toInt()

    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    extension.lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Accessibility defects are correctness defects for this audience.
        fatal += listOf("ContentDescription", "ClickableViewAccessibility")
        disable += listOf(
            // The catalogue is updated deliberately, not because lint nags.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
        )
        xmlReport = true
        htmlReport = true
        sarifReport = true
    }

    extension.packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/LICENSE*",
        "/META-INF/NOTICE*",
        "/META-INF/*.version",
        "DebugProbesKt.bin",
        "kotlin-tooling-metadata.json",
    )

    configureJavaToolchain()

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            applyHugMunCompilerArgs(this)
        }
    }
}

internal fun Project.configureJvmKotlin() {
    configureJavaToolchain()
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            applyHugMunCompilerArgs(this)
        }
    }
}

private fun Project.configureJavaToolchain() {
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(catalogVersion("jvmToolchain").toInt()))
        }
    }
}

/**
 * Compiler flags applied to every module.
 *
 * `allWarningsAsErrors` is on deliberately. This codebase is meant to be auditable by
 * people who are not Android developers, and a build that tolerates warnings quietly
 * accumulates the kind of ambiguity that costs a reviewer an afternoon.
 */
private fun applyHugMunCompilerArgs(options: KotlinCommonCompilerOptions) {
    options.allWarningsAsErrors.set(true)
    options.freeCompilerArgs.addAll(
        "-Xconsistent-data-class-copy-visibility",
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.time.ExperimentalTime",
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    )
}

internal fun Project.catalogVersion(alias: String): String =
    versionCatalog.findVersion(alias).get().requiredVersion
