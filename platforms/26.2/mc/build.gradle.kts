/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import heckerpowered.gradle.minecraftSourceConfigurations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.modDev)
}

val minecraftVersion = project.property("minecraftVersion").toString()
val javaVersion = libs.versions.javaBuild.get().toInt()

// Minecraft still requires Java 25 when this mod's bytecode targets Java 21.
configurations.matching { it.name in setOf("compileClasspath", "runtimeClasspath", "apiElements", "runtimeElements", "shadowRuntimeElements") }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion)
}

base {
    archivesName = "${providers.gradleProperty("modId").get()}-mc$minecraftVersion-platform"
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaModern.get()))
}

neoForge {
    neoFormVersion = project.property("neoFormVersion").toString()
}

dependencies {
    compileOnly(libs.mixinExtras)
    compileOnly(libs.asmTree)
    compileOnly(project(":render:render-hardware-interface"))
    compileOnly(project(":render:render-engine"))
    compileOnly(project(":render:render-hardware-interface-opengl"))
    compileOnly(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }

    implementation(project(":common"))
    implementation(project(":bridge"))
    implementation(project(":foundation:nbt"))
    implementation(project(":foundation:math"))
    compileOnly(libs.mixin)
}

val (minecraftJavaSources, minecraftKotlinSources, minecraftResources) = minecraftSourceConfigurations(exported = true)

artifacts {
    val javaSourceDirectory = layout.projectDirectory.dir("src/main/java")
    if (javaSourceDirectory.asFile.isDirectory) {
        add(minecraftJavaSources.name, javaSourceDirectory)
    }
    add(minecraftKotlinSources.name, layout.projectDirectory.dir("src/main/kotlin"))
    add(minecraftResources.name, layout.projectDirectory.dir("src/main/resources"))
}

// Forge Mixin 0.8.7 accepts Java 21 mixins; Kotlin uses the same target with the Java 25 toolchain.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.javaModern.get().toInt())
}

// Shared platform sources are compiled against each Minecraft host.
kotlin.sourceSets.main {
    kotlin.srcDir(rootProject.layout.projectDirectory.dir("platforms/shared/lwjgl3-hud/src/main/kotlin"))
}

artifacts {
    add(minecraftKotlinSources.name, rootProject.layout.projectDirectory.dir("platforms/shared/lwjgl3-hud/src/main/kotlin"))
}
