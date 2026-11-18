/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.gradle.jvm.application.tasks.CreateStartScripts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    `java-library`
    application
}

repositories { mavenCentral() }

// CPU compilation is assembled by the CLI or the GL3 factory. Codec consumers
// must not replace their host's LWJGL core. This classifier targets this build JVM.
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

val compilerExecution = configurations.create("compilerExecution") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":render:render-hardware-interface"))
    for (module in listOf("lwjgl", "lwjgl-shaderc", "lwjgl-spvc")) {
        val binding = "org.lwjgl:$module:$compilerLwjglVersion"
        val native = "$binding:$compilerNativeClassifier"
        compileOnly(binding)
        // Java 8 may run under Rosetta while the Gradle JVM runs on ARM64.
        add(compilerExecution.name, binding)
        if (compilerOperatingSystem.contains("mac")) {
            add(compilerExecution.name, "$binding:natives-macos")
            add(compilerExecution.name, "$binding:natives-macos-arm64")
        } else {
            add(compilerExecution.name, native)
        }
    }
}

application { mainClass.set("heckerpowered.render.compiler.OpenGLShaderCompilerMainKt") }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt())) }
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaLegacy.get())
}

kotlin {
    jvmToolchain(libs.versions.javaLegacy.get().toInt())
    compilerOptions { jvmTarget = JvmTarget.fromTarget(JavaVersion.toVersion(libs.versions.javaLegacy.get()).toString()) }
}

tasks.named<JavaExec>("run") {
    classpath = files(classpath, compilerExecution)
}

tasks.named<CreateStartScripts>("startScripts") {
    classpath = files(classpath, compilerExecution)
}

distributions {
    main {
        contents {
            from(compilerExecution) { into("lib") }
        }
    }
}
