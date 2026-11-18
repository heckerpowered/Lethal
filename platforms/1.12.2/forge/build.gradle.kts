/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import heckerpowered.gradle.consumeMinecraftSources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.GZIPInputStream

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    id("heckerpowered.convention.shadow")
    alias(libs.plugins.forgeGradle)
    alias(libs.plugins.forgeRenamer)
}

val minecraftProject = project(":platforms:1.12.2:mc")
evaluationDependsOn(minecraftProject.path)

val minecraftVersion = "1.12.2"
val forgeVersion = "14.23.5.2864"
val modVersion = project.version.toString()
val modId = providers.gradleProperty("modId").get()

val archiveName = "$modId-mc$minecraftVersion-forge$forgeVersion"

version = modVersion

base {
    archivesName = archiveName
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.minecraftLibsMaven)
    maven(fg.forgeMaven)
    mavenCentral()
}

dependencies {
    attach(libs.mixinExtras)
    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })
    annotationProcessor(libs.mixinExtras)

    attach(project(":common"))
    attach(project(":bridge"))
    attach(project(":foundation:nbt"))
    attach(project(":foundation:math"))
    attach(project(":render:render-hardware-interface"))
    attach(project(":render:render-engine"))
    attach(project(":render:render-hardware-interface-opengl-lwjgl2"))
    attach(libs.mixin) {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    // kotlin(...) uses the same version as the applied Kotlin Gradle plugin.
    attach(kotlin("stdlib"))
}

minecraft {
    mappings("stable", "39-1.12")
    accessTransformers.from(minecraftProject.layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        configureEach {
            workingDir = layout.projectDirectory.dir("run")

            systemProperty("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            systemProperty("forge.logging.console.level", "trace")
            systemProperty("mixin.env.disableRefMap", "true")

            jvmArgs.add("-Dfml.coreMods.load=heckerpowered.lethal.gameplay.common.core.CorePlugin")
        }

        register("client") {
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        register("server") {
            args("--nogui")

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

java {
    // Minecraft 1.12.2 requires Java 8, overriding the shared fallback toolchain.
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.javaLegacy.get().toInt())
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

val mixinProcessorMappings = layout.buildDirectory.file("tmp/mixin/processor-mappings.tsrg")
val mixinOutputMappings = layout.buildDirectory.file("tmp/mixin/generated-mappings.tsrg")
val mixinRefmap = layout.buildDirectory.file("tmp/mixin/refmap.json")
val mixinInputMappings = minecraft.dependency.toSrgFile

val prepareMixinMappings = tasks.register("prepareMixinMappings") {
    description = "Extracts the ForgeGradle SRG mapping file for the Mixin annotation processor."

    inputs.file(mixinInputMappings).withPropertyName("inputMappings")
    outputs.file(mixinProcessorMappings).withPropertyName("mixinProcessorMappings")

    doLast {
        val inputFile = inputs.files.singleFile
        val outputFile = outputs.files.singleFile

        outputFile.parentFile.mkdirs()

        GZIPInputStream(inputFile.inputStream()).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

tasks.compileJava {
    dependsOn(prepareMixinMappings)

    // The annotation processor receives these paths as strings, so declare the files explicitly
    // to make incremental builds and the build cache track the real inputs and outputs.
    inputs.file(mixinProcessorMappings).withPropertyName("mixinProcessorMappings")
    outputs.file(mixinOutputMappings).withPropertyName("mixinOutputMappings")
    outputs.file(mixinRefmap).withPropertyName("mixinRefmap")

    // Use the UTF-8 charset for Java compilation
    // This is done by default in Java 18+, but this ensures it no matter the Java or Gradle version
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-AreobfTsrgFile=${mixinProcessorMappings.get().asFile.canonicalPath}",
            "-AoutTsrgFile=${mixinOutputMappings.get().asFile.canonicalPath}",
            "-AoutRefMapFile=${mixinRefmap.get().asFile.canonicalPath}",
            "-AmappingTypes=tsrg",
            "-AdefaultObfuscationEnv=searge"
        )
    )
}

val reobfMappings = renamer.merge("reobfMappings") {
    dependsOn(tasks.compileJava)

    map(minecraft.dependency.toSrgFile)
    map(files(mixinOutputMappings))
}

val mixinConfigName = "mixins.$modId.json"
val mixinRefmapName = "mixins.$modId.refmap.json"
val modManifestAttributes = mapOf(
    "FMLCorePlugin" to "heckerpowered.lethal.gameplay.common.core.CorePlugin",
    "FMLCorePluginContainsFMLMod" to true,
    "MixinConfigs" to mixinConfigName,
    "Specification-Title" to "Lethal",
    "Specification-Vendor" to "Heckerpowered Corporation",
    "Specification-Version" to "1",
    "Implementation-Title" to project.name,
    "Implementation-Version" to project.version,
    "Implementation-Vendor" to "Heckerpowered"
)

tasks.jar {
    archiveBaseName = archiveName
    archiveVersion = modVersion
    archiveClassifier = "dev"

    // FML discovers resource packs from mod JARs, not their common-library dependencies.
    from(project(":common").layout.projectDirectory.dir("src/main/resources")) {
        include("assets/**")
    }

    manifest {
        attributes(modManifestAttributes)
    }
    from(mixinRefmap) {
        into("")
        rename("refmap\\.json", mixinRefmapName)
    }
}

val reobfJar = renamer.classes("reobfJar", tasks.jar) {
    dependsOn(reobfMappings)
    map = files(reobfMappings)

    archiveClassifier = "frontend"
}

tasks.processResources {
    val properties = mapOf(
        "version" to modVersion,
        "mcversion" to minecraftVersion,
    )

    inputs.property("properties", properties)

    filesMatching("mcmod.info") {
        expand(properties)
    }
}

tasks.assemble {
    dependsOn("reobfShadowJar")
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    relocate("com.llamalad7.mixinextras", "heckerpowered.lethal.shadow.mixinextras")
    // LaunchWrapper's ASM scanner cannot read the Java 16 ModLauncher integration bundled by Mixin.
    exclude("org/spongepowered/asm/launch/MixinLaunchPlugin.class")
    exclude("org/spongepowered/asm/launch/MixinTransformationService.class")
    exclude("org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncherEx*.class")
    exclude("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")
    exclude("META-INF/services/cpw.mods.modlauncher.serviceapi.ILaunchPluginService")

    // Renamer owns the final build/libs artifact, so Shadow writes only its reobfuscation input here.
    destinationDirectory = layout.buildDirectory.dir("tmp/shadowJar")

    from(mixinRefmap) {
        into("")
        rename("refmap\\.json", mixinRefmapName)
    }
}

val reobfShadowJar = renamer.classes("reobfShadowJar", shadowJar) {
    description = "Reobfuscate the shadow JAR"

    dependsOn(reobfMappings)
    map = files(reobfMappings)

    // The Shadow input already contains attached dependencies, so keep them out of Renamer's external library classpath.
    // Supplying both definitions can make Renamer omit relocated classes from the output.
    libraries.setFrom(
        configurations.named("compileClasspath").map { compileClasspath ->
            compileClasspath.minus(configurations.named("attach").get())
        }
    )

    archiveClassifier = null
}

shadowJar.configure {
    finalizedBy(reobfShadowJar)
}

gradle.projectsEvaluated {
    val developmentJar = tasks.jar
    val developmentOutput = sourceSets.main.get().output

    tasks.withType<JavaExec>()
        .matching { it.name == "runClient" || it.name == "runServer" }
        .configureEach {
            dependsOn(developmentJar)
            classpath = files(developmentJar.flatMap { it.archiveFile }).plus(classpath.minus(developmentOutput))
        }

    configurations.configureEach {
        if (name.startsWith("detachedConfiguration")) {
            // ForgeGradle 7 creates detached configurations for legacy run metadata/runtime resolution.
            // For Minecraft 1.12.2, some Mojang libraries such as java-objc-bridge natives are only available
            // from the Minecraft libraries repository, but those detached configurations may not inherit the
            // normal project repositories.
            repositories {
                maven(fg.minecraftLibsMaven)
            }
        }
    }
}

consumeMinecraftSources(minecraftProject)
