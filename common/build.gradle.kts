/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.ksp)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    add("ksp", project(":render:render-hardware-interface-codegen"))

    implementation(project(":bridge"))
    implementation(project(":foundation:nbt"))
    implementation(project(":foundation:math"))
    implementation(project(":render:render-hardware-interface"))
    implementation(project(":render:render-engine"))
}

java {
    // Keep common gameplay code usable by the legacy Forge 1.12.2 module.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt()))
    }

    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JavaVersion.toVersion(libs.versions.javaLegacy.get()).toString())
    }
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("performanceVersion", modVersion)
    filesMatching("lethal-performance.properties") { expand("version" to modVersion) }
}
