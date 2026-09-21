/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// Literal rather than `libs.plugins.spotless.get().pluginId`: ktlint's chain-method rule
// splits that three-link chain across five lines, which is worse than the string.
val ktlintVersion: String = libs.versions.ktlint.get()

allprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint(ktlintVersion)
                .editorConfigOverride(
                    mapOf(
                        "ktlint_standard_function-naming" to "disabled",
                        "ktlint_standard_property-naming" to "disabled",
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                        "max_line_length" to "120",
                    ),
                )
            licenseHeaderFile(rootProject.file("config/spotless/copyright.kt"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
        // No `format("misc")` block: Spotless 7.0.2's generic formatter is not
        // configuration-cache compatible under Gradle 9, and .editorconfig already
        // covers whitespace in Markdown and YAML for every editor that matters.
    }
}

detekt {
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(files(projectDir))
    ignoredBuildTypes = listOf("release")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/build/**", "**/resources/**")
    reports {
        html.required.set(true)
        sarif.required.set(true)
        md.required.set(false)
        txt.required.set(false)
    }
}

/**
 * One command that mirrors what CI enforces, so contributors never have to guess.
 *
 * The list is kept in step with `.github/workflows/ci.yml` by hand. It was once shorter
 * than CI — it ran formatting and static analysis but neither the tests nor a compile of
 * the Android modules — so `qualityCheck` reported success on a tree that did not build.
 * A green local gate that is weaker than the remote one is worse than no local gate.
 */
tasks.register("qualityCheck") {
    group = "verification"
    description = "Formatting, static analysis, lint, unit tests and a debug build — the same set CI runs."
    dependsOn(
        "spotlessCheck",
        "detekt",
        ":app:assembleDebug",
        gradle.includedBuilds.map { it.task(":convention:build") },
    )
    // Every module's own `check`, which is where AGP and the Kotlin plugin have already
    // wired up unit tests and lint. Naming them individually here would mean a new
    // module is silently unverified until someone remembers to add it.
    dependsOn(subprojects.map { "${it.path}:check" })
}
