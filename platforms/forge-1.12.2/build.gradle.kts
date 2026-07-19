/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.GZIPInputStream

plugins {
    id("heckerpowered.convention.kotlin-jvm")
    // Dynamic selectors are intentional: follow the newest version in each selected major line.
    id("net.minecraftforge.gradle") version "7.+"
    id("net.minecraftforge.renamer") version "1.+"
    id("com.gradleup.shadow") version "9.+"
}

val minecraftVersion = project.property("minecraftVersion").toString()
val forgeVersion = project.property("forgeVersion").toString()
val mixinVersion = project.property("mixinVersion").toString()
val modVersion = project.property("modVersion").toString()
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

val attached = configurations.create("attached") {
    isCanBeResolved = true
}

configurations.implementation {
    extendsFrom(attached)
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")

    testImplementation(kotlin("test"))

    attached(project(":common"))
    attached(project(":bridge"))
    attached("org.spongepowered:mixin:$mixinVersion") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    // kotlin(...) uses the same version as the applied Kotlin Gradle plugin.
    attached(kotlin("stdlib-jdk8"))
}

minecraft {
    mappings("stable", "39-1.12")

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
        languageVersion = JavaLanguageVersion.of(8)
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

    archiveClassifier = null
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
    dependsOn(reobfJar)
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    configurations = project.configurations.named("attached").map(::listOf)
    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveBaseName = project.base.archivesName
    archiveClassifier = "shadow"
    archiveVersion = project.version.toString()
    destinationDirectory = layout.buildDirectory.dir("tmp/shadowJar")

    from(mixinRefmap) {
        into("")
        rename("refmap\\.json", mixinRefmapName)
    }
    // The mod tracks recent Kotlin releases and uses APIs that may be unavailable in the older runtime provided by the mod environment.
    // Relocation isolates the bundled standard library and prevents linkage errors caused by incompatible Kotlin versions.
    // Revalidate this setup before adding kotlin-reflect or libraries that inspect Kotlin metadata, as they are tightly coupled to the standard library package.
    relocate("kotlin", "heckerpowered.lethal.shadow.kotlin")
    mergeServiceFiles()
}

val reobfShadowJar = renamer.classes("reobfShadowJar", shadowJar) {
    description = "Reobfuscate the shadow JAR"

    dependsOn(reobfMappings)
    map = files(reobfMappings)

    archiveClassifier = "shadow"
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
