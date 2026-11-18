/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import heckerpowered.gradle.consumeMinecraftSources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    id("heckerpowered.convention.shadow")
    alias(libs.plugins.fabricLoom)
}

val minecraftProject = project(":platforms:26.2:mc")
val minecraftVersion = minecraftProject.property("minecraftVersion").toString()
val minecraftVersionRange = "[$minecraftVersion]"
val javaVersion = libs.versions.javaBuild.get().toInt()
val modId = providers.gradleProperty("modId").get()

// Minecraft still requires Java 25 when this mod's bytecode targets Java 21.
configurations.matching { it.name in setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath", "apiElements", "runtimeElements", "shadowRuntimeElements") }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaModern.get()))
}

val fabricLoaderVersion = "0.19.5"
val fabricApiVersion = "0.161.0+26.2"

base {
    archivesName = "$modId-mc$minecraftVersion-fabric$fabricLoaderVersion"
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    attach(libs.mixinExtras)
    compileOnly(project(":render:render-engine"))
    compileOnly(project(":render:render-hardware-interface-opengl"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }

    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    attach(project(":common"))
    attach(project(":bridge"))
    attach(project(":foundation:nbt"))
    attach(project(":foundation:math"))
    attach(project(":render:render-hardware-interface"))
    attach(kotlin("stdlib"))
}

loom {
    accessWidenerPath.set(minecraftProject.layout.projectDirectory.file("src/main/resources/lethal.accesswidener"))
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            configuration(configurations.getByName("attach"))
        }
    }
    runs {
        named("client") {
            client()
            runDirectory.set(file("run/client"))
        }
        named("server") {
            server()
            runDirectory.set(file("run/server"))
        }
    }
}

val metadataProperties = mapOf(
    "mod_id" to modId,
    "mod_version" to project.version.toString(),
    "mod_name" to minecraftProject.property("modName").toString(),
    "mod_license" to minecraftProject.property("modLicense").toString(),
    "minecraft_version" to minecraftVersion,
    "minecraft_version_range" to minecraftVersionRange,
    "java_version" to javaVersion.toString(),
    "fabric_loader_version" to fabricLoaderVersion,
    "fabric_api_version" to fabricApiVersion,
)

tasks.processResources {
    val resourceProperties = metadataProperties
    inputs.properties(resourceProperties)
    filesMatching("fabric.mod.json") {
        expand(resourceProperties)
    }
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("com.llamalad7.mixinextras", "heckerpowered.lethal.shadow.mixinextras")
    archiveClassifier = ""
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

// Forge Mixin 0.8.7 accepts Java 21 mixins; Kotlin uses the same target with the Java 25 toolchain.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.javaModern.get().toInt())
    options.encoding = "UTF-8"
}

consumeMinecraftSources(minecraftProject)
