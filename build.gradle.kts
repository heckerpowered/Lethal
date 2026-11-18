/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

import net.neoforged.moddevgradle.dsl.ModDevExtension
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.RunConfigurationContainer

class ToolchainApplication(
    name: String,
    project: Project,
    private val generated: Application,
    private val javaHome: Provider<Directory>,
) : Application(name, project) {
    override fun toMap(): Map<String, *> = generated.toMap() +
            ("alternativeJrePath" to javaHome.get().asFile.absolutePath)
}

plugins {
    // Share ModDev and idea-ext classes across host projects during IDEA sync.
    alias(libs.plugins.modDev) apply false
}

val modVersion = libs.versions.mod.get()

allprojects {
    version = modVersion
    group = "heckerpowered.lethal"
}

subprojects {
    val host = this
    for (pluginId in listOf("net.neoforged.moddev", "net.neoforged.moddev.legacyforge")) {
        plugins.withId(pluginId) {
            extensions.getByType<ModDevExtension>().runs.configureEach {
                // Loader names repeat across Minecraft versions after the directory migration.
                ideName.set("${host.name}-${host.parent!!.name} - ${name.replaceFirstChar(Char::titlecase)}")
            }
        }
    }
}

// ModDev configures JavaExec's launcher but does not export it to IDEA's Application model.
// Wait until every generated configuration exists before supplying the matching toolchain.
gradle.projectsEvaluated {
    val ideaProject = rootProject.extensions.findByType<IdeaModel>()?.project ?: return@projectsEvaluated
    val settings = (ideaProject as ExtensionAware).extensions.getByType<ProjectSettings>()
    val configurations = (settings as ExtensionAware).extensions.getByType<RunConfigurationContainer>()
    rootProject.subprojects.forEach hostLoop@{ host ->
        val modDev = host.extensions.findByType<ModDevExtension>() ?: return@hostLoop
        val java = host.extensions.getByType<JavaPluginExtension>()
        val javaHome = host.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(java.toolchain.languageVersion)
        }.map { it.metadata.installationPath }
        modDev.runs.forEach runLoop@{ run ->
            val generated = configurations.findByName(run.ideName.get()) as? Application ?: return@runLoop
            configurations.remove(generated)
            configurations.add(ToolchainApplication(generated.name, host, generated, javaHome))
        }
    }
}
