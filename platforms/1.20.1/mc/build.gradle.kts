/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import heckerpowered.gradle.minecraftSourceConfigurations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    id("net.neoforged.moddev.legacyforge")
}

val minecraftVersion = "1.20.1"

base {
    archivesName = "${providers.gradleProperty("modId").get()}-mc$minecraftVersion-platform"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

kotlin {
    jvmToolchain(17)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget("17"))
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
}

legacyForge {
    mcpVersion = "1.20.1"
    accessTransformers.from(layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg"))
    validateAccessTransformers = true
    parchment {
        minecraftVersion = "1.20.1"
        mappingsVersion = "2023.09.03"
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":bridge"))
    implementation(project(":foundation:nbt"))
    implementation(project(":foundation:math"))
    compileOnly(project(":render:render-hardware-interface"))
    compileOnly(project(":render:render-engine"))
    compileOnly(libs.mixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.asmTree)
    compileOnly(project(":render:render-hardware-interface-opengl"))
    compileOnly(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }
}

val (minecraftJavaSources, minecraftKotlinSources, minecraftResources) = minecraftSourceConfigurations(exported = true)

artifacts {
    add(minecraftJavaSources.name, layout.projectDirectory.dir("src/main/java"))
    add(minecraftKotlinSources.name, layout.projectDirectory.dir("src/main/kotlin"))
    add(minecraftResources.name, layout.projectDirectory.dir("src/main/resources"))
}

kotlin.sourceSets.main {
    kotlin.srcDir(rootProject.layout.projectDirectory.dir("platforms/shared/lwjgl3-hud/src/main/kotlin"))
}

artifacts {
    add(minecraftKotlinSources.name, rootProject.layout.projectDirectory.dir("platforms/shared/lwjgl3-hud/src/main/kotlin"))
}
