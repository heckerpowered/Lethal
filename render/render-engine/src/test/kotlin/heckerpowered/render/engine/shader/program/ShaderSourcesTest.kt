/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import java.lang.reflect.Proxy
import kotlin.test.*

class ShaderSourcesTest {
    @Test
    fun failedReadsRetryAndCompilationRetriesTheFirstSuccessfulWholeEngineSnapshot() {
        var unavailable = true
        var loads = 0
        var compilationCalls = 0
        var failCompilation = true
        val requests = mutableListOf<Map<ShaderStage, String>>()
        val includes = linkedMapOf("effects/shared.glsl" to "A")
        val sources = ShaderSources { resources ->
            loads++
            requests += resources
            check(!unavailable) { "Generation unavailable" }
            resources.map { [stage, path] ->
                CanonicalShaderModule(
                    ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "A", "pack:$path")),
                    "effects/$path",
                    includes,
                )
            }
        }
        val shader = resourceShader()
        val compiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        val device = device(compiled) {
            compilationCalls++
            check(!failCompilation) { "Compilation temporarily unavailable" }
        }
        ResourceLifetime.build { this }.use { lifetime ->
            val programs = ShaderRealizations(device, lifetime, sources)
            assertEquals(0, loads)
            val readFailure = assertFailsWith<IllegalStateException> { programs.definition(shader) }
            assertEquals("Generation unavailable", readFailure.message)
            assertEquals(0, compilationCalls)
            unavailable = false
            val compileFailure = assertFailsWith<IllegalStateException> { programs.definition(shader) }
            assertEquals("Compilation temporarily unavailable", compileFailure.message)
            assertEquals(2, loads)
            unavailable = true
            includes["effects/shared.glsl"] = "changed after capture"
            failCompilation = false
            val prepared = programs.definition(shader)
            assertSame(prepared, programs.definition(shader))
            assertEquals(2, loads)
            assertEquals(3, compilationCalls)
            assertEquals(2, compiled.size)
            assertTrue(compiled.all { [module, contents] -> (module.code as ShaderSource).text == "A" && contents.getValue("effects/shared.glsl") == "A" })
            assertEquals(mapOf(ShaderStage.Vertex to "mesh.vert", ShaderStage.Fragment to "mesh.frag"), requests.last())
            assertFailsWith<UnsupportedOperationException> { (requests.last() as MutableMap<ShaderStage, String>).clear() }
        }
    }

    @Test
    fun oneResourceDefinitionSelectsIndependentSourceSnapshotsInDifferentEngines() {
        var loads = 0
        fun sources(generation: String) = ShaderSources { resources ->
            loads++
            resources.map { [stage, path] ->
                CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, generation, path)), path, mapOf("shared.glsl" to generation))
            }
        }
        val shader = resourceShader()
        val firstCompiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        val secondCompiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        ResourceLifetime.build { this }.use { lifetime ->
            val first = ShaderRealizations(device(firstCompiled), lifetime, sources("A"))
            val second = ShaderRealizations(device(secondCompiled), lifetime, sources("B"))
            val firstDefinition = first.definition(shader)
            val secondDefinition = second.definition(shader)
            assertNotSame(firstDefinition, secondDefinition)
            assertSame(firstDefinition, first.definition(shader))
            assertSame(secondDefinition, second.definition(shader))
            assertEquals(2, loads)
            assertTrue(firstCompiled.all { [module, contents] -> (module.code as ShaderSource).text == "A" && contents.getValue("shared.glsl") == "A" })
            assertTrue(secondCompiled.all { [module, contents] -> (module.code as ShaderSource).text == "B" && contents.getValue("shared.glsl") == "B" })
        }
    }

    @Test
    fun invalidStageGenerationIsRetriedBeforeAnyCompilation() {
        var valid = false
        var loads = 0
        val sources = ShaderSources { resources ->
            loads++
            resources.filterKeys { valid || it == ShaderStage.Vertex }.map { [stage, path] ->
                CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "generation", path)), path)
            }
        }
        val compiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        val shader = resourceShader()
        ResourceLifetime.build { this }.use { lifetime ->
            val programs = ShaderRealizations(device(compiled), lifetime, sources)
            assertFailsWith<IllegalArgumentException> { programs.definition(shader) }
            assertEquals(1, loads)
            assertTrue(compiled.isEmpty())
            valid = true
            programs.definition(shader)
            assertEquals(2, loads)
            assertEquals(2, compiled.size)
        }
    }

    @Test
    fun defaultResourcesKeepDiagnosticLabelsAndRelativeOriginsAndSupplyOnlyRoots() {
        val vertex = "/assets/render-engine/shaders/fullscreen.vert"
        val fragment = "/assets/render-engine/shaders/copy.frag"
        val modules = ClasspathShaderSources.load(linkedMapOf(ShaderStage.Vertex to vertex, ShaderStage.Fragment to fragment))
        assertEquals(listOf(ShaderStage.Vertex, ShaderStage.Fragment), modules.map { it.description.stage })
        assertEquals(listOf("fullscreen.vert", "copy.frag"), modules.map { it.origin })
        assertEquals(listOf(vertex, fragment), modules.map { (it.description.code as ShaderSource).label })
        assertTrue(modules.all { (it.description.code as ShaderSource).text.startsWith("#version 450") && it.includes.isEmpty() })
    }

    @Test
    fun missingDefaultResourceFailsDuringPreparationBeforeDeviceCompilation() {
        val shader = MeshShader<Unit>(shaderDefinition("missing") {
            vertex("/missing-shader-source.vert")
            fragment("/assets/render-engine/shaders/copy.frag")
            inputs { emptyInputs() }
        }) { error("Preparation does not bind values") }
        val compiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        ResourceLifetime.build { this }.use { lifetime ->
            val programs = ShaderRealizations(device(compiled), lifetime)
            val failure = assertFailsWith<IllegalStateException> { programs.definition(shader) }
            assertEquals("Missing shader classpath resource: /missing-shader-source.vert", failure.message)
            assertTrue(compiled.isEmpty())
        }
    }

    @Test
    fun callerSelectedCanonicalCodeKeepsItsSharedSnapshotWithoutEngineResourceIO() {
        val unusedSources = ShaderSources { error("Canonical memory must not use engine resource IO") }
        var captures = 0
        val modules = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
            CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "generated", stage.name)), stage.name)
        }
        val shader = MeshShader<Unit>(shaderDefinition("generated") {
            canonical { captures++; modules }
            inputs { emptyInputs() }
        }) { error("Preparation does not bind values") }
        val compiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        ResourceLifetime.build { this }.use { lifetime ->
            ShaderRealizations(device(compiled), lifetime, unusedSources).definition(shader)
            ShaderRealizations(device(compiled), lifetime, unusedSources).definition(shader)
            assertEquals(1, captures)
            assertEquals(4, compiled.size)
        }
    }

    @Test
    fun callerSelectedNativeCodeNeedsNeitherResourceIONorCanonicalCompilation() {
        val unusedSources = ShaderSources { error("Native code must not use engine resource IO") }
        val modules = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
            ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "native", stage.name))
        }
        val shader = MeshShader<Unit>(shaderDefinition("native") { native(modules, emptyInputs()) }) { error("Preparation does not bind values") }
        val compiled = mutableListOf<Pair<ShaderModuleDescription, Map<String, String>>>()
        ResourceLifetime.build { this }.use { lifetime ->
            val first = ShaderRealizations(device(compiled), lifetime, unusedSources).definition(shader)
            val second = ShaderRealizations(device(compiled), lifetime, unusedSources).definition(shader)
            assertSame(first, second)
            assertTrue(compiled.isEmpty())
        }
    }

    private fun resourceShader(): MeshShader<Unit> = MeshShader(shaderDefinition("generation") {
        vertex("mesh.vert")
        fragment("mesh.frag")
        inputs { emptyInputs() }
    }) { error("Preparation does not bind values") }

    private fun emptyInputs(): ShaderInputLayout = ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList())

    private fun device(compiled: MutableList<Pair<ShaderModuleDescription, Map<String, String>>>, beforeCompilation: () -> Unit = {}): GraphicsDevice = GraphicsDevice::class.java.cast(
        Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, method, arguments ->
            check(method.name == "compileCanonicalShader")
            beforeCompilation()
            val description = arguments[0] as ShaderModuleDescription
            @Suppress("UNCHECKED_CAST")
            val includes = arguments[2] as Map<String, String>
            compiled += description to includes
            ShaderCompilation(description, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        },
    )
}
