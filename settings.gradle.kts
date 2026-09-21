@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "HugMun"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// -----------------------------------------------------------------------------
// Application
// -----------------------------------------------------------------------------
include(":app")

// -----------------------------------------------------------------------------
// Core — shared infrastructure
// -----------------------------------------------------------------------------
include(":core:model") // pure Kotlin domain types
include(":core:common") // dispatchers, time, math, Result
include(":core:designsystem") // the "Рассветный воздух" design language
include(":core:database") // Room 3
include(":core:datastore") // preferences
include(":core:data") // repositories
include(":core:domain") // use cases
include(":core:testing") // shared test infrastructure

// -----------------------------------------------------------------------------
// Engines — the measurement path. Pure Kotlin where at all possible, because
// every number these produce has to be explainable and unit-testable.
// -----------------------------------------------------------------------------
include(":engine:psychophysics") // adaptive staircases, thresholds, trial models
include(":engine:scheduling") // FSRS-6 and the ACTIVE booster protocol
include(":engine:signal") // PPG extraction, HRV metrics, spectral analysis
include(":engine:audio") // 40 Hz amplitude-modulated synthesis
include(":engine:visuals") // frame-locked photic stimulation

// -----------------------------------------------------------------------------
// Features
// -----------------------------------------------------------------------------
include(":feature:onboarding")
include(":feature:home")
include(":feature:vigilance") // «Зоркость»  — speed of processing (UFOV)
include(":feature:breathing") // «Дыхание»   — resonance breathing + HRV
include(":feature:anchor") // «Якорь»     — spaced retrieval
include(":feature:rhythm") // «Ритм»      — 40 Hz sensory stimulation
include(":feature:assessment") // «Замер»     — measurement battery
include(":feature:insights") // trends, reliable change
include(":feature:library") // evidence cards and citations
include(":feature:settings")

// -----------------------------------------------------------------------------
// Performance
// -----------------------------------------------------------------------------
include(":benchmark")
