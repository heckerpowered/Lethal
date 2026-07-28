/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.convention

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
}

val attach = configurations.create("attach") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.named("implementation") {
    extendsFrom(attach)
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(attach)
    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveClassifier = "shadow"

    // The mod tracks recent Kotlin releases and uses APIs that may be unavailable in the older runtime provided by the mod environment.
    // Relocation isolates the bundled standard library and prevents linkage errors caused by incompatible Kotlin versions.
    // Revalidate this setup before adding kotlin-reflect or libraries that inspect Kotlin metadata, as they are tightly coupled to the standard library package.
    relocate("kotlin", "heckerpowered.lethal.shadow.kotlin")
    mergeServiceFiles()
}
