plugins {
    alias(libs.plugins.hugmun.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.engine.psychophysics)
    api(projects.engine.scheduling)
}
