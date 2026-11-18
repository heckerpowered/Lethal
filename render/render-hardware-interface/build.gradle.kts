/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import heckerpowered.gradle.GenerateMemoryStackAllocations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
}

val memoryStackAllocationSources = layout.buildDirectory.dir("generated/sources/memoryStack/main/kotlin")
val generateMemoryStackAllocations = tasks.register<GenerateMemoryStackAllocations>("generateMemoryStackAllocations") {
    description = "Generates MemoryStack allocation overloads for arities 1 through 16."
    outputDirectory.set(memoryStackAllocationSources)
}

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.bundles.lwjgl3)

    add("ksp", project(":render:render-hardware-interface-codegen"))
}

java {
    // Keep common gameplay code usable by the legacy Forge 1.12.2 module.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt()))
    }

    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(memoryStackAllocationSources)
    }

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JavaVersion.toVersion(libs.versions.javaLegacy.get()).toString())
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateMemoryStackAllocations)
}

tasks.matching { it.name == "kspKotlin" }.configureEach {
    dependsOn(generateMemoryStackAllocations)
}
