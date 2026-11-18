/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import heckerpowered.gradle.consumeMinecraftSources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.slf4j.event.Level

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    id("heckerpowered.convention.shadow")
    alias(libs.plugins.modDev)
}

val minecraftProject = project(":platforms:1.21.1:mc")
evaluationDependsOn(minecraftProject.path)

val minecraftVersion = project.property("minecraftVersion").toString()
val minecraftVersionRange = "[$minecraftVersion]"
val neoVersion = project.property("neoVersion").toString()
val loaderVersionRange = project.property("loaderVersionRange").toString()
val parchmentMinecraftVersion = minecraftVersion
val parchmentMappingsVersion = project.property("parchmentMappingsVersion").toString()
val modId = providers.gradleProperty("modId").get()
val modName = project.property("modName").toString()
val modLicense = project.property("modLicense").toString()
val modVersion = libs.versions.mod.get()

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
        languageVersion = JavaLanguageVersion.of(libs.versions.javaModern.get().toInt())
    }

    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaModern.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaModern.get())
}

kotlin {
    jvmToolchain(libs.versions.javaModern.get().toInt())

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.javaModern.get())
    }
}

neoForge {
    version = neoVersion
    accessTransformers.from(minecraftProject.layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg"))
    validateAccessTransformers = true

    parchment {
        minecraftVersion = parchmentMinecraftVersion
        mappingsVersion = parchmentMappingsVersion
    }

    runs {
        register("client") {
            client()
        }

        register("server") {
            server()
            programArgument("--nogui")
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = Level.DEBUG
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            // Keep shared packages in one mod module during development runs.
            listOf(
                ":common",
                ":bridge",
                ":foundation:math",
                ":foundation:nbt",
                ":render:render-hardware-interface",
                ":render:render-engine",
                ":render:render-hardware-interface-opengl",
                ":render:render-hardware-interface-opengl-lwjgl3",
                ":render:render-hardware-interface-shader-compiler",
            ).forEach { dependencyPath ->
                evaluationDependsOn(dependencyPath)
                sourceSet(project(dependencyPath).extensions.getByType<SourceSetContainer>().named("main").get())
            }
        }
    }
}

dependencies {
    // NeoForge provides MixinExtras as its own runtime module.
    compileOnly(libs.mixinExtras)

    attach(project(":common"))
    attach(project(":bridge"))
    attach(project(":foundation:nbt"))
    attach(project(":foundation:math"))
    attach(project(":render:render-hardware-interface"))
    attach(project(":render:render-engine"))
    attach(project(":render:render-hardware-interface-opengl"))
    attach(project(":render:render-hardware-interface-shader-compiler"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }
    // kotlin(...) uses the same version as the applied Kotlin Gradle plugin.
    attach(kotlin("stdlib"))

    // ModDevGradle requires non-mod libraries on this classpath for 1.21.8 and earlier runs.
    add("additionalRuntimeClasspath", kotlin("stdlib"))
    add("additionalRuntimeClasspath", libs.kotlinxSerializationCore)
}

// These project outputs already belong to lethal's grouped development mod.
// Loading their JARs again creates separate modules exporting the same packages.
val attachedProjectArtifacts = configurations["attach"].incoming.artifactView {
    componentFilter { it is ProjectComponentIdentifier }
}.files

sourceSets.main {
    runtimeClasspath = output + (configurations.runtimeClasspath.get() - attachedProjectArtifacts)
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

tasks.jar { archiveClassifier = "dev" }

tasks.assemble { dependsOn(tasks.named("shadowJar")) }

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    dependencies {
        exclude(dependency("org.jetbrains:annotations:.*"))
        exclude(dependency("io.github.llamalad7:mixinextras-.*:.*"))
    }
}

// Shared platform sources are compiled against each Minecraft host.
kotlin.sourceSets.main {
    kotlin.srcDir(rootProject.layout.projectDirectory.dir("platforms/shared/neoforge-client-config/src/main/kotlin"))
}

consumeMinecraftSources(minecraftProject)
