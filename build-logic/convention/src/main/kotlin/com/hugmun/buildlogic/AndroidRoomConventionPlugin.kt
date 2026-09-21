/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

public class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<KspExtension> {
            // Schemas are committed. A migration that is not backed by a schema diff
            // in review is a migration nobody has checked.
            arg("room.schemaLocation", "${projectDir}/schemas")
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("api", library("androidx-room3-runtime"))
            add("implementation", library("androidx-sqlite-bundled"))
            add("ksp", library("androidx-room3-compiler"))
            add("testImplementation", library("androidx-room3-testing"))
        }
    }
}
