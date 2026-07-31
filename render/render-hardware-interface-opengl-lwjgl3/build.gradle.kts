/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":render:render-hardware-interface"))
    testImplementation(kotlin("test"))
}