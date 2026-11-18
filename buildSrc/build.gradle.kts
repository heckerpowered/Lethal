/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.shadowGradlePlugin)
    constraints {
        implementation("org.apache.ant:ant:1.10.18") {
            because("Fix CVE-2026-78254 in Shadow's transitive Ant dependency")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.javaBuild.get().toInt())
}
