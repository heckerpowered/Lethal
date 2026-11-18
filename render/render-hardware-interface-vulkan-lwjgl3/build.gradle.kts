/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

val compilerLwjglVersion = libs.versions.lwjglCompiler.get()
val compilerArchitecture = System.getProperty("os.arch").lowercase()
val compilerOperatingSystem = System.getProperty("os.name").lowercase()
val compilerNativeClassifier = when {
    compilerOperatingSystem.contains("mac") -> if (compilerArchitecture == "aarch64") "natives-macos-arm64" else "natives-macos"
    compilerOperatingSystem.contains("win") -> when (compilerArchitecture) {
        "aarch64" -> "natives-windows-arm64"
        "x86", "i386" -> "natives-windows-x86"
        else -> "natives-windows"
    }

    compilerArchitecture == "aarch64" -> "natives-linux-arm64"
    compilerArchitecture.startsWith("arm") -> "natives-linux-arm32"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":render:render-hardware-interface"))
    implementation(project(":render:render-hardware-interface-vulkan"))
    implementation(project(":render:render-hardware-interface-shader-compiler"))
    compileOnly(libs.lwjglCompilerCore)
    compileOnly(libs.lwjglCompilerVulkan)
    for (module in listOf("lwjgl", "lwjgl-shaderc", "lwjgl-spvc")) {
        runtimeOnly("org.lwjgl:$module:$compilerLwjglVersion")
        runtimeOnly("org.lwjgl:$module:$compilerLwjglVersion:$compilerNativeClassifier")
    }
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
