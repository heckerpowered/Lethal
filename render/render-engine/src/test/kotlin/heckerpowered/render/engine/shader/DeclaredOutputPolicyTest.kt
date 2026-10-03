/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.shader

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderArea
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.pass.geometryPipeline
import heckerpowered.render.engine.shader.program.MeshShaderInput
import heckerpowered.render.engine.shader.program.MeshShader
import heckerpowered.render.engine.shader.program.shaderDefinition
import heckerpowered.render.engine.shader.binding.VertexInputMapping
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.DrawRange
import heckerpowered.render.pipeline.primitive.PrimitiveState
import heckerpowered.render.pipeline.primitive.PrimitiveTopology
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStage
import heckerpowered.render.shader.ShaderSource
import heckerpowered.render.shader.ShaderLanguage
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.shader.ShaderStages
import java.lang.reflect.Proxy
import kotlin.test.*

class DeclaredOutputPolicyTest {
    @Test
    fun explicitDepthPathRejectsAlwaysOnTopAndKeepsSeeThroughDisabled() {
        val shaders = Proxy.newProxyInstance(ShaderStages::class.java.classLoader, arrayOf(ShaderStages::class.java)) { _, _, _ ->
            error("Unexpected GPU access")
        } as ShaderStages
        val base = RenderPipelineDescription("declared", shaders, colorTargets = emptyList())
        val target = RenderPassDescription("target", renderArea = RenderArea(0, 0, 1, 1))
        val shader = MeshShader<Unit>(shaderDefinition("output validation") {
            native(
                listOf(
                    ShaderModuleDescription(ShaderStage.Vertex, ShaderSource(ShaderLanguage.Glsl, "void main(){}", label = "output validation vertex")),
                    ShaderModuleDescription(ShaderStage.Fragment, ShaderSource(ShaderLanguage.Glsl, "void main(){}", label = "output validation fragment")),
                ),
                ShaderInputLayout(
                    VertexInputMapping(emptyList()),
                    emptyList(),
                    emptyList(),
                ),
            )
        }) { MeshShaderInput(ShaderGeometry(ParameterValues(), GeometrySelection.Vertices(DrawRange.vertices(3)), PrimitiveState(PrimitiveTopology.TriangleList))) }
        val element = shader.bind(Unit)
        val failure = assertFailsWith<IllegalArgumentException> {
            geometryPipeline(base, element, target, DepthMode.AlwaysOnTop, CompareFunction.Always, emptyList())
        }
        assertTrue(failure.message.orEmpty().contains("AlwaysOnTop"))
        val seeThrough = geometryPipeline(base, element, target, DepthMode.SeeThrough, CompareFunction.Always, emptyList())
        assertNull(seeThrough.depthStencil)
    }
}
