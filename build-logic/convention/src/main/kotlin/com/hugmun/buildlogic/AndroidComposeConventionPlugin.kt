/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

public class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val extension = extensions.findByName("android") as CommonExtension
        extension.buildFeatures.compose = true

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            // Written on every build so that recomposition regressions are visible in
            // review rather than discovered on a slow device in someone's hand.
            val metricsDir = layout.buildDirectory.dir("compose-metrics")
            metricsDestination.set(metricsDir)
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))

            stabilityConfigurationFiles.add(
                rootProject.layout.projectDirectory.file("config/compose-stability.conf"),
            )
        }

        dependencies {
            val bom = library("androidx-compose-bom")
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", library("androidx-compose-foundation"))
            add("implementation", library("androidx-compose-runtime"))
            add("implementation", library("androidx-compose-ui"))
            add("implementation", library("androidx-compose-ui-graphics"))
            add("implementation", library("androidx-compose-ui-tooling-preview"))
            add("implementation", library("androidx-lifecycle-runtime-compose"))

            add("debugImplementation", library("androidx-compose-ui-tooling"))
            add("debugImplementation", library("androidx-compose-ui-test-manifest"))

            add("androidTestImplementation", library("androidx-compose-ui-test-junit4"))
        }
    }
}
