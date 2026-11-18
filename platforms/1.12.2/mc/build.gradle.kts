/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import heckerpowered.gradle.minecraftSourceConfigurations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.forgeGradle)
}

val minecraftVersion = "1.12.2"

base {
    archivesName = "${providers.gradleProperty("modId").get()}-mc$minecraftVersion-platform"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

kotlin {
    jvmToolchain(8)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget("1.8"))
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.minecraftLibsMaven)
    maven(fg.forgeMaven)
    mavenCentral()
}

minecraft {
    mappings("stable", "39-1.12")
    accessTransformers.from(layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg"))
}

dependencies {
    implementation(minecraft.dependency("net.minecraft:joined:$minecraftVersion"))
    compileOnly(project(":render:render-hardware-interface-opengl-lwjgl2"))
    implementation(project(":common"))
    implementation(project(":bridge"))
    implementation(project(":foundation:nbt"))
    implementation(project(":foundation:math"))
    compileOnly(project(":render:render-hardware-interface"))
    compileOnly(project(":render:render-engine"))
    compileOnly(libs.lwjgl2)
    compileOnly(libs.mixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.asmTree)
}

val (minecraftJavaSources, minecraftKotlinSources, minecraftResources) = minecraftSourceConfigurations(exported = true)

artifacts {
    add(minecraftJavaSources.name, layout.projectDirectory.dir("src/main/java"))
    add(minecraftKotlinSources.name, layout.projectDirectory.dir("src/main/kotlin"))
    add(minecraftResources.name, layout.projectDirectory.dir("src/main/resources"))
}
