/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Pure-Kotlin modules. Used for everything on the measurement path so that the
 * psychophysics, DSP and scheduling logic can be tested on the JVM in milliseconds,
 * with no emulator and no Android framework in the way.
 */
public class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        configureJvmKotlin()

        dependencies {
            add("implementation", library("kotlinx-coroutines-core"))
            add("testImplementation", library("junit4"))
            add("testImplementation", library("kotlinx-coroutines-test"))
        }

        tasks.withType<Test>().configureEach {
            testLogging {
                events("failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
                showStackTraces = true
            }
        }
    }
}
