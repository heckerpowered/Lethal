/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
    implementation(project(":render:render-hardware-interface"))
    implementation(libs.bundles.lwjgl3)

    testImplementation(kotlin("test"))
}