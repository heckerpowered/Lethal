/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.toMatrix4
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.draw.PreparedDrawCommand
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.pipeline.rasterization.RasterizationState
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Prepares geometry with its directly bound shader for ordinary color raster passes.
 *
 * The processor interprets the shader's geometry and parameter contract, selects pass-dependent
 * pipeline state, and resolves resources into one prepared command. Placement and raster scope
 * remain properties of the submission; shader labels are used only for diagnostics.
 *
 * Zero-work ranges and geometry outside an explicit conservative bound produce no draws.
 * Unsafe replay requests fail before device resources are established.
 */
internal class GeometryElementPassProcessor(
    private val programs: ShaderRealizations,
    private val rasterization: RasterizationState = RasterizationState(),
) : RenderElementPassProcessor<GeometryElement> {
    override fun prepare(element: GeometryElement, submission: RenderSubmission, pass: RasterPass, preparation: PassPreparation): List<PreparedDrawCommand> {
        val geometry = element.geometry
        if (geometry.range.isEmpty || !visible(submission, pass.inputs)) return emptyList()

        val shader = element.shading.shader
        require(!pass.requireReplaySafe || shader.replaySafe) { "Shader '${shader.label}' cannot be replayed in multiple passes" }

        val shaderInputs = programs.definition(shader).inputs
        val scope = submission.rasterScope
        val viewport = scope.viewport ?: Viewport.from(pass.description.renderArea)
        val parameterValues = resolveParameters(element, submission, pass, shaderInputs, viewport)
        val vertexState = shaderInputs.vertices.lower(geometry)
        val colorTargets = geometryColorTargets(element, pass.description, shader.outputs, parameterValues, preparation.targetAlphaQuantities)
        validateColorContent(shader.outputs, parameterValues, colorTargets, preparation.targetAlphaQuantities, element.compositions)

        val program = programs.require(shader)
        val basePipeline = RenderPipelineDescription(
            label = shader.label,
            shaders = program.stages,
            layout = program.layout,
            vertex = vertexState,
            primitive = geometry.primitive,
            rasterization = rasterization,
            colorTargets = emptyList(),
        )
        val pipeline = geometryPipeline(basePipeline, element, pass.description, submission.visibility, pass.inputs.depthCompare, colorTargets)

        val vertexBuffers = prepareVertexBuffers(geometry, vertexState, preparation)
        val indexSource = when (val selection = geometry.selection) {
            is GeometrySelection.Vertices -> null
            is GeometrySelection.Indexed -> selection.source
        }
        val indexInput = preparation.indices(indexSource)
        val descriptorSets = shaderInputs.descriptors.mapIndexed { setIndex, descriptor ->
            setIndex to descriptor.resolve(parameterValues, preparation)
        }.toMap()
        val pushConstants = shaderInputs.pushes.map { push -> push.resolve(parameterValues) }
        val scissor = scope.scissors.fold(ScissorRectangle.from(pass.description.renderArea), ScissorRectangle::intersect)
        return listOf(
            PreparedDrawCommand(
                pipeline = pipeline,
                vertexBuffers = vertexBuffers,
                indexInput = indexInput,
                descriptorSets = descriptorSets,
                pushConstants = pushConstants,
                arguments = geometry.range,
                viewport = viewport,
                scissor = scissor,
                stencilReference = scope.stencilReference,
            )
        )
    }

    private fun resolveParameters(element: GeometryElement, submission: RenderSubmission, pass: RasterPass, inputs: ShaderInputLayout, viewport: Viewport): ParameterValues {
        val viewValues = pass.inputs.forObject(submission.objectState, viewport)
        val shadingValues = viewValues.mergedWith(element.shading.parameters)
        val geometry = element.geometry
        val geometryValues = if (geometry is ShaderGeometry) shadingValues.mergedWith(geometry.parameters) else shadingValues
        val values = inputs.derivations.resolve(geometryValues, submission.objectState.localToWorld.toMatrix4())

        inputs.parameterNames.forEach(values::require)
        return values
    }

    private fun prepareVertexBuffers(geometry: RenderGeometry, vertex: VertexState, preparation: PassPreparation): Map<Int, GpuBufferView> {
        if (geometry !is VertexGeometry) return emptyMap()
        val buffers = LinkedHashMap<Int, GpuBufferView>()
        for ([slot, source] in geometry.streams.withIndex()) {
            if (vertex.buffers[slot].attributes.isEmpty()) continue
            buffers[slot] = preparation.stream(source)
        }
        return buffers
    }
}
