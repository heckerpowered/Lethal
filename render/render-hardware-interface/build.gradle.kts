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
    compileOnly(libs.lwjgl3.core)

    add("ksp", project(":render:render-hardware-interface-codegen"))
    add("kspTest", project(":render:render-hardware-interface-codegen"))

    testImplementation(kotlin("test"))
}

java {
    // Keep common gameplay code usable by the legacy Forge 1.12.2 module.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(memoryStackAllocationSources)
    }

    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateMemoryStackAllocations)
}

tasks.matching { it.name == "kspKotlin" || it.name == "kspTestKotlin" }.configureEach {
    dependsOn(generateMemoryStackAllocations)
}
