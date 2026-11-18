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
    alias(libs.plugins.modDev)
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

val neoVersion = "26.2.0.88"

base {
    archivesName = "$modId-mc$minecraftVersion-neoforge$neoVersion"
}

repositories {
    mavenCentral()
}

dependencies {
    // NeoForge provides MixinExtras as its own runtime module.
    compileOnly(libs.mixinExtras)
    compileOnly(project(":render:render-engine"))
    compileOnly(project(":render:render-hardware-interface-opengl"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }

    attach(project(":common"))
    attach(project(":bridge"))
    attach(project(":foundation:nbt"))
    attach(project(":foundation:math"))
    attach(project(":render:render-hardware-interface"))
    attach(kotlin("stdlib"))
}

kotlin.sourceSets.main {
    kotlin.srcDir(rootProject.layout.projectDirectory.dir("platforms/shared/neoforge-client-config/src/main/kotlin"))
}

neoForge {
    version = neoVersion
    runs {
        register("client") {
            client()
            gameDirectory.set(layout.projectDirectory.dir("run/client"))
        }
        register("server") {
            server()
            gameDirectory.set(layout.projectDirectory.dir("run/server"))
            programArgument("--nogui")
        }
    }
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            listOf(":common", ":bridge", ":foundation:math", ":foundation:nbt", ":render:render-hardware-interface", ":render:render-engine", ":render:render-hardware-interface-opengl", ":render:render-hardware-interface-opengl-lwjgl3", ":render:render-hardware-interface-shader-compiler").forEach { dependencyPath ->
                sourceSet(project(dependencyPath).extensions.getByType<SourceSetContainer>().named("main").get())
            }
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
    "neo_version" to neoVersion,
)

tasks.processResources {
    val resourceProperties = metadataProperties
    inputs.properties(resourceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
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
    options.release.set(libs.versions.javaModern.get().toInt())
    options.encoding = "UTF-8"
}

consumeMinecraftSources(minecraftProject)
