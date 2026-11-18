/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    `java-library`
}

repositories {
    maven("https://libraries.minecraft.net") {
        content { excludeGroup("org.lwjgl") }
    }
    mavenCentral()
}

val compilerArchive = configurations.create("compilerArchive") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val compilerBindings = configurations.create("compilerBindings") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val compilerNativeClassifiers = listOf(
    "natives-linux",
    "natives-linux-arm32",
    "natives-linux-arm64",
    "natives-macos",
    "natives-macos-arm64",
    "natives-windows",
    "natives-windows-arm64",
    "natives-windows-x86",
)
val compilerNatives = compilerNativeClassifiers.associateWith { classifier ->
    configurations.create("compiler_" + classifier.replace('-', '_')) {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
}
val compilerModules = listOf("lwjgl", "lwjgl-shaderc", "lwjgl-spvc")
val compilerLwjglVersion = libs.versions.lwjglCompiler.get()

dependencies {
    api(project(":render:render-hardware-interface"))
    api(project(":render:render-hardware-interface-opengl"))
    implementation(project(":render:render-hardware-interface-shader-compiler"))
    compileOnly(libs.lwjgl2)

    add(compilerArchive.name, project(":render:render-hardware-interface-shader-compiler"))
    add(compilerBindings.name, kotlin("stdlib"))
    for (module in compilerModules) {
        add(compilerBindings.name, "org.lwjgl:$module:$compilerLwjglVersion")
        for ((classifier, configuration) in compilerNatives) {
            add(configuration.name, "org.lwjgl:$module:$compilerLwjglVersion:$classifier")
        }
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

// Nested archives are private resources; CPU bindings must never become host classpath entries.
tasks.processResources {
    val bundle = "heckerpowered/render/opengl/lwjgl2/compiler-runtime"
    from(compilerArchive) {
        into(bundle)
        rename { "compiler.jar" }
    }
    from(compilerBindings) {
        into(bundle)
        rename { it.replace(Regex("-[0-9].*\\.jar$"), ".jar") }
    }
    for ((classifier, configuration) in compilerNatives) {
        from(configuration) {
            into("$bundle/natives/$classifier")
            rename { it.replace(Regex("-[0-9].*\\.jar$"), ".jar") }
        }
    }
}
