/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinxSerializationCore)
}

java {
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
