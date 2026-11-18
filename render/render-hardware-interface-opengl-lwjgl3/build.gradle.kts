/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
    implementation(project(":render:render-hardware-interface"))
    implementation(project(":render:render-hardware-interface-opengl"))
    implementation(project(":render:render-hardware-interface-shader-compiler"))
    compileOnly(libs.lwjgl3Core)
    compileOnly(libs.lwjgl3OpenGL)
}

// Both adapters deploy the same private compiler bundle, including every supported native classifier.
evaluationDependsOn(":render:render-hardware-interface-opengl-lwjgl2")
val compilerBundle = project(":render:render-hardware-interface-opengl-lwjgl2").tasks.named<ProcessResources>("processResources")
tasks.processResources {
    from(compilerBundle) { include("heckerpowered/render/opengl/lwjgl2/compiler-runtime/**") }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt()))
    }

    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
}

kotlin {
    jvmToolchain(libs.versions.javaLegacy.get().toInt())

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JavaVersion.toVersion(libs.versions.javaLegacy.get()).toString())
    }
}
