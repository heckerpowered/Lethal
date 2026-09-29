/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":foundation:math"))
    implementation(project(":render:render-hardware-interface"))
    
    add("ksp", project(":render:render-hardware-interface-codegen"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
