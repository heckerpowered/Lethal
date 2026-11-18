/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinxSerializationCore)

    implementation(project(":render:render-hardware-interface"))
    implementation(project(":foundation:math"))
}

java {
    // Keep the shared bridge API usable by the legacy Forge 1.12.2 module.
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
