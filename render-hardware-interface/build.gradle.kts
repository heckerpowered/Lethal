/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

dependencies {
    testImplementation(kotlin("test"))
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
