/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":render:render-hardware-interface"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt()))
    }
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
}

kotlin {
    jvmToolchain(libs.versions.javaLegacy.get().toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JavaVersion.toVersion(libs.versions.javaLegacy.get()).toString())
    }
}
