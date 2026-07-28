/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.+"
}

rootProject.name = "Lethal"
include("common")
include("bridge")
include("platforms:forge-1.12.2")
include("platforms:neoforge-1.21.1")
include("render-hardware-interface")
include("render-hardware-interface-codegen")
