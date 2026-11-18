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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Lethal"

include("foundation:math")
include("foundation:nbt")

include("common")
include("bridge")

include("platforms:1.12.2:mc")
include("platforms:1.12.2:forge")
include("platforms:1.20.1:mc")
include("platforms:1.20.1:forge")
include("platforms:1.20.1:neoforge")
include("platforms:1.21.1:mc")
include("platforms:1.21.1:neoforge")
include("platforms:26.1.2:mc")
include("platforms:26.1.2:neoforge")

include("platforms:26.2:mc")
include("platforms:26.2:fabric")
include("platforms:26.2:forge")
include("platforms:26.2:neoforge")

include("render:render-hardware-interface")
include("render:render-hardware-interface-codegen")
include("render:render-hardware-interface-opengl")
include("render:render-hardware-interface-opengl-lwjgl2")
include("render:render-hardware-interface-opengl-lwjgl3")
include("render:render-hardware-interface-vulkan")
include("render:render-hardware-interface-vulkan-lwjgl3")
include("render:render-hardware-interface-shader-compiler")
include("render:render-engine")
