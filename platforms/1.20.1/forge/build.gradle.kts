/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

/* SPDX-License-Identifier: MIT */

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    id("heckerpowered.convention.shadow")
    id("net.neoforged.moddev.legacyforge")
}

apply(from = rootProject.file("platforms/gradle/legacy-forge.gradle"))
