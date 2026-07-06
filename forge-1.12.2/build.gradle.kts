/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.GZIPInputStream

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    id("net.minecraftforge.gradle") version "7.+"
    id("net.minecraftforge.renamer") version "1.+"
}

val minecraftVersion = project.property("minecraftVersion").toString()
val forgeVersion = project.property("forgeVersion").toString()
val mixinVersion = project.property("mixinVersion").toString()
val modId = project.property("modId").toString()
val modVersion = project.property("modVersion").toString()

val archiveName = "$modId-mc$minecraftVersion-forge$forgeVersion"

version = modVersion

base {
    archivesName.set(archiveName)
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.minecraftLibsMaven)
    maven(fg.forgeMaven)
}

dependencies {
    implementation(project(":bridge"))
    implementation(project(":common"))

    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    // annotationProcessor("net.minecraftforge:eventbus-validator:7.0.1")
    implementation("org.spongepowered:mixin:$mixinVersion") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")

    testImplementation(kotlin("test"))
}

minecraft {
    mappings("stable", "39-1.12")

    runs {
        configureEach {
            workingDir = layout.projectDirectory.dir("run")

            systemProperty("forge.logging.markers", "SCAN,REGISTRIES,REGISTRYDUMP")
            systemProperty("forge.logging.console.level", "trace")

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

sourceSets {
    main {
        resources {
            srcDir("src/generated/resources")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

val mixinProcessorMappings = layout.buildDirectory.file("tmp/mixin/processor-mappings.tsrg")
val mixinOutputMappings = layout.buildDirectory.file("tmp/mixin/generated-mappings.tsrg")
val mixinRefmap = layout.buildDirectory.file("tmp/mixin/refmap.json")

val prepareMixinMappings = tasks.register("prepareMixinMappings") {
    description = "Extracts the ForgeGradle SRG mapping file for the Mixin annotation processor."

    val inputMappings = minecraft.dependency.toSrgFile

    inputs.file(inputMappings)
        .withPropertyName("inputMappings")

    outputs.file(mixinProcessorMappings)
        .withPropertyName("mixinProcessorMappings")

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

val reobfJar = renamer.classes("reobfJar", tasks.jar) {
    dependsOn(reobfMappings)
    map = files(reobfMappings)

    archiveClassifier.set("")
}

val mixinConfigName = "mixins.$modId.json"
val mixinRefmapName = "mixins.$modId.refmap.json"

tasks.jar {
    archiveBaseName.set(archiveName)
    archiveVersion.set(modVersion)
    archiveClassifier.set("dev")

    manifest {
        attributes(
            "FMLCorePlugin" to "heckerpowered.lethal.gameplay.common.core.CorePlugin",
            "FMLCorePluginContainsFMLMod" to true,
            "MixinConfigs" to mixinConfigName
        )
    }

    from(mixinRefmap) {
        into("")
        rename("refmap\\.json", mixinRefmapName)
    }
}

tasks.assemble {
    dependsOn(reobfJar)
}

tasks.test {
    useJUnitPlatform()
}

gradle.projectsEvaluated {
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

val isMacOs = System.getProperty("os.name")
    .lowercase()
    .contains("mac")
val isAppleSilicon = System.getProperty("os.arch") == "aarch64"
val x64JavaHome = providers.gradleProperty("minecraft_x64_java_home")

tasks.configureEach {
    if (name == "runClient" && isMacOs && isAppleSilicon && x64JavaHome.isPresent) {
        // Minecraft 1.12.2 uses LWJGL 2 natives for macOS x86_64 only.
        // On Apple Silicon, run the legacy client with an x86_64 JDK configured in the user's ~/.gradle/gradle.properties.
        System.setProperty("org.gradle.java.home", x64JavaHome.get())
    }
}