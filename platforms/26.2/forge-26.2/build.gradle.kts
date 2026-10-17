/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    id("heckerpowered.convention.shadow")
    alias(libs.plugins.forgeGradle)
}

val minecraftProject = project(":platforms:26.2:mc-26.2")
val minecraftVersion = libs.versions.minecraft262.get()
val minecraftVersionRange = "[$minecraftVersion]"
val javaVersion = libs.versions.javaBuild.get().toInt()
val modId = providers.gradleProperty("modId").get()

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
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaModern.get()))
}

val forgeVersion = libs.versions.forge262.get()

base {
    archivesName = "$modId-mc$minecraftVersion-forge$forgeVersion"
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    compileOnly(libs.mixinExtras)
    compileOnly(project(":render:render-engine"))
    compileOnly(project(":render:render-hardware-interface-opengl"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl3")) {
        exclude(group = "org.lwjgl")
    }

    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))

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

minecraft {
    accessTransformers.from(minecraftProject.layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg"))
    runs {
        configureEach {
            workingDir = layout.projectDirectory.dir("run/$name")
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }
        register("client") {
            if (System.getProperty("os.name").startsWith("Mac")) {
                jvmArgs.add("-XstartOnFirstThread")
            }
        }
        register("server") {
            args("--nogui")
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
    "forge_version" to forgeVersion,
    "loader_version_range" to libs.versions.forgeLoader262.get(),
)

tasks.processResources {
    val resourceProperties = metadataProperties
    inputs.properties(resourceProperties)
    filesMatching("META-INF/mods.toml") {
        expand(resourceProperties)
    }
}

tasks.jar {
    archiveClassifier = "dev"
    manifest.attributes["MixinConfigs"] = "mixins.lethal.json"
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    dependencies {
        exclude(dependency("org.jetbrains:annotations:.*"))
    }
}

// Forge resolves library JARs as JPMS modules, so the mod's split packages must stay together.
val attachedProjectArtifacts = configurations["attach"].incoming.artifactView {
    componentFilter { it is ProjectComponentIdentifier }
}.files

// Minecraft's launch metadata supplies the unsafe core; Forge also declares the ordinary core.
val ordinaryLwjglCore = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.filter {
    it.moduleVersion.id.group == "org.lwjgl" && it.name == "lwjgl" && it.classifier == null
}.map { it.file }

sourceSets.main {
    runtimeClasspath = files(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }) + (configurations.runtimeClasspath.get() - attachedProjectArtifacts - files(ordinaryLwjglCore))
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

// Forge Mixin 0.8.7 accepts Java 21 mixins; Kotlin uses the same target with the Java 25 toolchain.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.javaModern.get().toInt())
    options.encoding = "UTF-8"
}
