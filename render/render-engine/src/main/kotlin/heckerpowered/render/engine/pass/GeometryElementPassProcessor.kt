/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.math.toMatrix4
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.ScissorRectangle
import heckerpowered.render.command.pass.Viewport
import heckerpowered.render.engine.draw.*
import heckerpowered.render.engine.geometry.GeometrySelection
import heckerpowered.render.engine.geometry.RenderGeometry
import heckerpowered.render.engine.geometry.ShaderGeometry
import heckerpowered.render.engine.geometry.VertexGeometry
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.prepare.PassPreparation
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.RenderSubmission
import heckerpowered.render.engine.shader.binding.ShaderInputLayout
import heckerpowered.render.engine.shader.program.MeshShading
import heckerpowered.render.engine.shader.program.ShaderRealizations
import heckerpowered.render.pipeline.multisample.MultisampleState
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.pipeline.rasterization.RasterizationState
import heckerpowered.render.pipeline.vertex.VertexState
import heckerpowered.render.resource.buffer.GpuBufferView

/**
 * Prepares geometry with its directly bound shader for ordinary color raster passes.
 *
 * The processor interprets the shader's geometry and parameter contract, selects pass-dependent
 * pipeline state, and resolves resources into one prepared draw. Placement and raster scope
 * remain properties of the submission; shader labels are used only for diagnostics.
 *
 * Zero-work ranges and geometry outside an explicit conservative bound produce no draws.
 */
internal class GeometryElementPassProcessor(
    private val programs: ShaderRealizations,
    private val rasterization: RasterizationState = RasterizationState(),
) : RenderElementPassProcessor<GeometryElement> {

    override fun prepare(element: GeometryElement, submission: RenderSubmission, pass: RasterPass, preparation: PassPreparation): List<PreparedDraw> {
        val geometry = element.geometry
        if (geometry.range.isEmpty || !visible(submission, pass.inputs)) return emptyList()

        val shading = element.shading
        val shader = shading.shader
        val inputs = programs.definition(shader).inputs

        val state = resolveDrawState(submission, pass)
        val parameters = resolveParameters(element, submission, pass, inputs, state.viewport)
        val vertex = inputs.vertices.lower(geometry)

        val pipeline = resolvePipeline(element, submission, pass, vertex, parameters, preparation)
        val preparedGeometry = prepareGeometry(geometry, vertex, preparation)
        val bindings = prepareBindings(shading, inputs, parameters, preparation)
        return listOf(PreparedDraw(pipeline, preparedGeometry, bindings, state))
    }

    private fun resolveDrawState(submission: RenderSubmission, pass: RasterPass): DrawDynamicState {
        val scope = submission.rasterScope
        val area = pass.description.renderArea

        val viewport = scope.viewport ?: Viewport.from(area)
        val scissor = scope.scissors.fold(ScissorRectangle.from(area), ScissorRectangle::intersect)
        return DrawDynamicState(viewport, scissor, scope.stencilReference)
    }

    private fun resolveParameters(element: GeometryElement, submission: RenderSubmission, pass: RasterPass, inputs: ShaderInputLayout, viewport: Viewport): ParameterValues {
        val viewValues = pass.inputs.forObject(submission.objectState, viewport)
        val shadingValues = viewValues.mergedWith(element.shading.parameters)

        val geometryValues = when (val geometry = element.geometry) {
            is ShaderGeometry -> shadingValues.mergedWith(geometry.parameters)
            else -> shadingValues
        }

        val localToWorld = submission.objectState.localToWorld.toMatrix4()
        val values = inputs.derivations.resolve(geometryValues, localToWorld)

        inputs.parameterNames.forEach(values::require)
        return values
    }

    private fun resolvePipeline(element: GeometryElement, submission: RenderSubmission, pass: RasterPass, vertex: VertexState, parameters: ParameterValues, preparation: PassPreparation): RenderPipelineDescription {
        val shader = element.shading.shader
        val description = pass.description

        val colorTargets = resolveColorTargets(element, description, shader.outputs, parameters, preparation.targetAlphaQuantities)
        val depthStencil = resolveDepthStencil(element, description, submission.visibility, pass.inputs.depthCompare)
        val multisample = MultisampleState(description.attachmentSampleCount ?: SampleCount.One)

        val program = programs.require(shader)
        return RenderPipelineDescription(shader.label, program.stages, program.layout, vertex, element.geometry.primitive, rasterization, multisample, colorTargets, depthStencil)
    }

    private fun prepareGeometry(geometry: RenderGeometry, vertex: VertexState, preparation: PassPreparation): PreparedGeometry {
        val buffers = LinkedHashMap<Int, GpuBufferView>()

        if (geometry is VertexGeometry) {
            for ([slot, source] in geometry.streams.withIndex()) {
                if (vertex.buffers[slot].attributes.isEmpty()) continue
                buffers[slot] = preparation.stream(source)
            }
        }

        val invocation = when (val selection = geometry.selection) {
            is GeometrySelection.Vertices -> DrawInvocation.Vertices(selection.range)
            is GeometrySelection.Indexed -> DrawInvocation.Indexed(selection.range, preparation.indices(selection.source))
        }

        return PreparedGeometry(buffers, invocation)
    }

    private fun prepareBindings(shading: MeshShading, inputs: ShaderInputLayout, values: ParameterValues, preparation: PassPreparation): ShaderBindings {
        val descriptorSets = inputs.descriptors.mapIndexed { index, descriptor -> index to descriptor.resolve(values, preparation) }.toMap()

        val pushConstants = when (val prepare = shading.preparePushConstants) {
            null -> inputs.pushes.map { it.resolve(values) }
            else -> preparation.device.memoryStack.frame {
                context(this) { prepare() }
            }
        }

        return ShaderBindings(descriptorSets, pushConstants)
    }
}