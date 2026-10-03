/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader.program

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.shader.*
import heckerpowered.render.shader.reflection.ShaderInterfaceDescription
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderDefinitionTest {
    @Test
    fun sourceRepresentationRemainsExplicitAndIndependentOfOutputDeclarations() {
        val defaultOutput = shaderDefinition("default output") {
            native(modules(), emptyInputs())
            sourceRepresentation = AlphaRepresentation.Premultiplied
        }
        val explicitOutput = shaderDefinition("explicit output") {
            native(modules(), emptyInputs())
            sourceRepresentation = AlphaRepresentation.Premultiplied
            output(0, FragmentOutput(AlphaRepresentation.Straight))
        }
        assertEquals(AlphaRepresentation.Premultiplied, defaultOutput.outputs.getValue(0).representation)
        assertEquals(AlphaRepresentation.Premultiplied, explicitOutput.sourceRepresentation)
        assertEquals(AlphaRepresentation.Straight, explicitOutput.outputs.getValue(0).representation)
        val shader = MeshShader<Unit>(explicitOutput) { MeshShaderInput(geometry()) }
        assertEquals(explicitOutput.sourceRepresentation, shader.sourceRepresentation)
    }

    @Test
    fun declaredOutputsAreCapturedAndCanBeEmpty() {
        lateinit var builder: ShaderDefinitionBuilder
        val definition = shaderDefinition("captured") {
            builder = this
            native(modules(), emptyInputs())
            output(2, FragmentOutput(AlphaRepresentation.Premultiplied))
        }
        builder.noColorOutputs()
        assertEquals(setOf(2), definition.outputs.keys)
        assertFailsWith<UnsupportedOperationException> { (definition.outputs as MutableMap<Int, FragmentOutput>).clear() }
        val depthOnly = shaderDefinition("depth only") {
            native(modules(), emptyInputs())
            noColorOutputs()
        }
        assertTrue(depthOnly.outputs.isEmpty())
    }

    @Test
    fun typedBindingsShareFirstSuccessfulGenerationAndFailedSourceReadsRemainRetryable() {
        var generation = "A"
        var failLoad = true
        var reads = 0
        var mappings = 0
        val definition = shaderDefinition("shared") {
            canonical {
                listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
                    reads++
                    check(!failLoad) { "generation unavailable" }
                    CanonicalShaderModule(ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, generation, stage.name)), stage.name)
                }
            }
            inputs {
                assertTrue(vertex.inputs.isEmpty() && fragment.inputs.isEmpty())
                mappings++
                emptyInputs()
            }
        }
        val first = MeshShader<Unit>(definition) { MeshShaderInput(geometry()) }
        val second = MeshShader<Int>(definition) { MeshShaderInput(geometry()) }
        first.bind(Unit)
        second.bind(1)
        assertEquals(0, reads)
        val compiled = mutableListOf<String>()
        val firstDevice = device(compiled)
        val failure = assertFailsWith<IllegalStateException> { first.resolve(firstDevice) }
        assertEquals("generation unavailable", failure.message)
        assertEquals(1, reads)
        assertTrue(compiled.isEmpty())
        failLoad = false
        first.resolve(firstDevice)
        generation = "B"
        second.resolve(device(compiled))
        assertEquals(3, reads)
        assertEquals(2, mappings)
        assertEquals(List(4) { "A" }, compiled)
    }

    private fun modules(): List<ShaderModuleDescription> = listOf(ShaderStage.Vertex, ShaderStage.Fragment).map { stage ->
        ShaderModuleDescription(stage, ShaderSource(ShaderLanguage.Glsl, "native fixture", stage.name))
    }

    private fun emptyInputs(): ShaderInputLayout = ShaderInputLayout(VertexInputMapping(emptyList()), emptyList(), emptyList())

    private fun geometry(): ShaderGeometry = ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState())

    private fun device(compiled: MutableList<String>): GraphicsDevice = GraphicsDevice::class.java.cast(
        Proxy.newProxyInstance(GraphicsDevice::class.java.classLoader, arrayOf(GraphicsDevice::class.java)) { _, method, arguments ->
            check(method.name == "compileCanonicalShader")
            val description = arguments[0] as ShaderModuleDescription
            compiled += (description.code as ShaderSource).text
            ShaderCompilation(description, ShaderInterfaceDescription(emptyList(), emptyList(), emptyList()))
        },
    )
}
