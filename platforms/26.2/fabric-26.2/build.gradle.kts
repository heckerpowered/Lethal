/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    id("heckerpowered.convention.shadow")
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

val minecraftProject = project(":platforms:26.2:mc-26.2")
val minecraftVersion = minecraftProject.property("minecraftVersion").toString()
val minecraftVersionRange = minecraftProject.property("minecraftVersionRange").toString()
val javaVersion = minecraftProject.property("javaVersion").toString().toInt()
val modId = providers.gradleProperty("modId").get()
version = minecraftProject.property("modVersion").toString()

// Minecraft still requires Java 25 when this mod's bytecode targets Java 21.
configurations.matching { it.name in setOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath", "apiElements", "runtimeElements", "shadowRuntimeElements") }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion)
}

val minecraftJavaSources = configurations.create("minecraftJavaSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val minecraftKotlinSources = configurations.create("minecraftKotlinSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val minecraftResources = configurations.create("minecraftResources") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

val fabricLoaderVersion = project.property("fabricLoaderVersion").toString()
val fabricApiVersion = project.property("fabricApiVersion").toString()

base {
    archivesName = "$modId-mc$minecraftVersion-fabric$fabricLoaderVersion"
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    attach("io.github.llamalad7:mixinextras-common:0.5.4")
    compileOnly(project(":render:render-engine"))
    compileOnly(project(":render:render-hardware-interface-opengl"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }

    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    add(minecraftJavaSources.name, project(path = minecraftProject.path, configuration = "minecraftJavaSources"))
    add(minecraftKotlinSources.name, project(path = minecraftProject.path, configuration = "minecraftKotlinSources"))
    add(minecraftResources.name, project(path = minecraftProject.path, configuration = "minecraftResources"))

    attach(project(":common"))
    attach(project(":bridge"))
    attach(project(":foundation:nbt"))
    attach(project(":foundation:math"))
    attach(project(":render:render-hardware-interface"))
    attach(kotlin("stdlib"))
}

// IDEA reads source roots across projects, so resolve these directory-only exports in their owning project.
sourceSets.main {
    java.srcDir(files(minecraftJavaSources.files).builtBy(minecraftJavaSources))
    resources.srcDir(files(minecraftResources.files).builtBy(minecraftResources))
}

kotlin.sourceSets.main {
    kotlin.srcDir(files(minecraftKotlinSources.files).builtBy(minecraftKotlinSources))
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
            runDir("run/client")
        }
        named("server") {
            server()
            runDir("run/server")
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
    archiveClassifier = ""
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

// Forge Mixin 0.8.7 accepts Java 21 mixins; Kotlin uses the same target with the Java 25 toolchain.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}
