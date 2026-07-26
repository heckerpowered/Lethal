/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":bridge"))
    implementation(project(":render-hardware-interface"))
}

java {
    // Keep common gameplay code usable by the legacy Forge 1.12.2 module.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
