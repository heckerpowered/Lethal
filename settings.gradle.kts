/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.+"
}

rootProject.name = "Lethal"

include("foundation:math")

include("common")
include("bridge")

include("platforms:forge-1.12.2")
include("platforms:neoforge-1.21.1")
include("platforms:26.2:mc-26.2")
include("platforms:26.2:fabric-26.2")
include("platforms:26.2:forge-26.2")
include("platforms:26.2:neoforge-26.2")

include("render:render-hardware-interface")
include("render:render-hardware-interface-codegen")
include("render:render-hardware-interface-opengl")
include("render:render-hardware-interface-opengl-lwjgl2")
include("render:render-hardware-interface-opengl-lwjgl3")
include("render:render-hardware-interface-vulkan")
include("render:render-hardware-interface-vulkan-lwjgl3")
include("render:render-hardware-interface-shader-compiler")
include("render:render-engine")