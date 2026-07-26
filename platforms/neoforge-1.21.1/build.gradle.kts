/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.slf4j.event.Level

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    id("heckerpowered.convention.shadow")
    // Dynamic selectors are intentional: follow the newest version in the selected major line.
    id("net.neoforged.moddev") version "2.+"
}

val minecraftVersion = project.property("minecraftVersion").toString()
val minecraftVersionRange = project.property("minecraftVersionRange").toString()
val neoVersion = project.property("neoVersion").toString()
val loaderVersionRange = project.property("loaderVersionRange").toString()
val parchmentMinecraftVersion = project.property("parchmentMinecraftVersion").toString()
val parchmentMappingsVersion = project.property("parchmentMappingsVersion").toString()
val modId = providers.gradleProperty("modId").get()
val modName = project.property("modName").toString()
val modLicense = project.property("modLicense").toString()
val modVersion = project.property("modVersion").toString()

val archiveName = "$modId-mc$minecraftVersion-neoforge$neoVersion"

version = modVersion

base {
    archivesName = archiveName
}

repositories {
    mavenCentral()
}

java {
    // Minecraft 1.21.1 requires Java 21, overriding the shared fallback toolchain.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

neoForge {
    version = neoVersion
    validateAccessTransformers = true

    parchment {
        minecraftVersion = parchmentMinecraftVersion
        mappingsVersion = parchmentMappingsVersion
    }

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))

    attach(project(":common"))
    attach(project(":bridge"))
    // kotlin(...) uses the same version as the applied Kotlin Gradle plugin.
    attach(kotlin("stdlib"))

    // ModDevGradle requires non-mod libraries on this classpath for 1.21.8 and earlier runs.
    add("additionalRuntimeClasspath", project(":common"))
    add("additionalRuntimeClasspath", project(":bridge"))
    add("additionalRuntimeClasspath", kotlin("stdlib"))
}

val modMetadataProperties = mapOf(
    "minecraft_version" to minecraftVersion,
    "minecraft_version_range" to minecraftVersionRange,
    "neo_version" to neoVersion,
    "loader_version_range" to loaderVersionRange,
    "mod_id" to modId,
    "mod_name" to modName,
    "mod_license" to modLicense,
    "mod_version" to modVersion,
)

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    description = "Generates NeoForge mod metadata from the project properties."

    inputs.properties(modMetadataProperties)
    expand(modMetadataProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}

neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
