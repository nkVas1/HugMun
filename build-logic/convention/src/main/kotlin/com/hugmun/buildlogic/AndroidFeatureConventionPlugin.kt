/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything a `:feature:*` module needs, so that feature build files stay three lines
 * long and the dependency shape of the app is uniform and reviewable.
 */
public class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("hugmun.android.library")
        pluginManager.apply("hugmun.android.compose")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:domain"))

            add("implementation", library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", library("androidx-lifecycle-runtime-compose"))
            add("implementation", library("androidx-navigation3-runtime"))
            add("implementation", library("androidx-compose-material3"))
            add("implementation", library("kotlinx-datetime"))

            add("testImplementation", project(":core:testing"))
            add("testImplementation", library("turbine"))
        }
    }
}
