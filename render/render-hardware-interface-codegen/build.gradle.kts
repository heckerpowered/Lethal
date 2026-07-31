/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kspSymbolProcessingAPI)

    testImplementation(kotlin("test"))
}
