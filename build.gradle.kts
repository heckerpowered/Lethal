import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.versions)
}

allprojects {
    group = "heckerpowered.lethal"
}

subprojects {
    val versionPropertyName = "${name.replace("-", "_")}_version"
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    revision = "release"
    gradleReleaseChannel = "current"

    rejectVersionIf {
        val v = candidate.version.lowercase()
        listOf("alpha", "beta", "rc", "eap", "milestone").any { it in v }
    }
}