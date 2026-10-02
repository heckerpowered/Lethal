/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.CompositingMode
import heckerpowered.render.engine.material.parameter.ParameterValues
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.engine.scene.GeometryElement
import heckerpowered.render.engine.scene.drawing.DepthMode
import heckerpowered.render.engine.shader.program.FragmentOutput
import heckerpowered.render.engine.support.collection.toUnmodifiableList
import heckerpowered.render.pipeline.color.ColorTargetState
import heckerpowered.render.pipeline.color.ColorWriteMask
import heckerpowered.render.pipeline.depthstencil.CompareFunction
import heckerpowered.render.pipeline.depthstencil.DepthState
import heckerpowered.render.pipeline.depthstencil.DepthStencilState
import heckerpowered.render.pipeline.multisample.MultisampleState
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.texture.TextureFormat

internal fun resolvedFragmentOutput(outputs: Map<Int, FragmentOutput>, slot: Int, values: ParameterValues): FragmentOutput? {
    val declaration = outputs[slot] ?: return null
    val name = declaration.alphaFromTexture ?: return declaration
    val texture = values.require(name) as? TextureParameterValue ?: error("Output $slot derives alpha meaning from a texture value")
    return declaration.copy(alphaQuantity = texture.sampledAlphaQuantity, alphaFromTexture = null)
}

internal fun geometryColorTargets(element: GeometryElement, target: RenderPassDescription, outputs: Map<Int, FragmentOutput>, values: ParameterValues, destinations: Map<Int, AlphaQuantity>): List<ColorTargetState> {
    require(element.compositions.keys.all { target.colorAttachments.getOrNull(it) != null }) { "Composition location requires a present color attachment" }

    return target.colorAttachments.mapIndexed { slot, use ->
        if (use == null) return@mapIndexed ColorTargetState(TextureFormat.Rgba8UnsignedNormalized, writeMask = ColorWriteMask.None)

        val format = use.attachment.format
        val source = resolvedFragmentOutput(outputs, slot, values)
        val destination = if (!format.colorComponents.alpha) AlphaQuantity.Coverage else destinations[slot]
        val composition = element.compositions[slot] ?: CompositingMode.Replace

        val state = composition.target(format, source, destination)
        require(state.format == format) { "Composition returned a different format at location $slot" }
        state
    }.toUnmodifiableList()
}

internal fun geometryPipeline(base: RenderPipelineDescription, element: GeometryElement, target: RenderPassDescription, visibility: DepthMode, depthCompare: CompareFunction, colorTargets: List<ColorTargetState>): RenderPipelineDescription {
    require(visibility != DepthMode.AlwaysOnTop) { "AlwaysOnTop requires a separate depth phase" }

    val declaredDepthStencil = element.depthStencil
    validateDeclaredDepthStencil(declaredDepthStencil, target, visibility)

    val defaultDepthStencil = defaultDepthStencil(base, target, visibility, depthCompare)
    val selectedDepthStencil = declaredDepthStencil ?: defaultDepthStencil
    val selectedDepth = selectedDepthStencil?.depth
    val comparedDepthStencil = if (element.usesViewDepthCompare && selectedDepth != null)
        selectedDepthStencil.copy(depth = selectedDepth.copy(compareFunction = depthCompare)) else selectedDepthStencil

    val requestedDepthWrite = element.depthWrite
    require(requestedDepthWrite != true || visibility != DepthMode.SeeThrough) { "SeeThrough cannot write depth" }
    require(requestedDepthWrite != true || comparedDepthStencil?.depth != null) { "Depth writes require active depth processing and an attachment" }
    val comparedDepth = comparedDepthStencil?.depth
    val effectiveDepthStencil = if (requestedDepthWrite == null || comparedDepth == null)
        comparedDepthStencil else comparedDepthStencil.copy(depth = comparedDepth.copy(writeEnabled = requestedDepthWrite))

    return base.copy(
        colorTargets = colorTargets,
        depthStencil = effectiveDepthStencil,
        multisample = MultisampleState(target.attachmentSampleCount ?: SampleCount.One)
    )
}

private fun validateDeclaredDepthStencil(declaredDepthStencil: DepthStencilState?, target: RenderPassDescription, visibility: DepthMode) {
    if (declaredDepthStencil == null) return

    require((target.depthAttachment ?: target.stencilAttachment)?.attachment?.format == declaredDepthStencil.format) { "Depth/stencil format mismatch" }
    require(declaredDepthStencil.depth == null || target.depthAttachment != null) { "Depth processing requires an attachment" }
    require(declaredDepthStencil.stencil == null || target.stencilAttachment != null) { "Stencil processing requires an attachment" }
    require(visibility != DepthMode.SeeThrough || declaredDepthStencil.depth == null) { "SeeThrough cannot enable depth processing" }
}

private fun defaultDepthStencil(base: RenderPipelineDescription, target: RenderPassDescription, visibility: DepthMode, depthCompare: CompareFunction): DepthStencilState? {
    return (target.depthAttachment ?: target.stencilAttachment)?.attachment?.let { attachment ->
        val format = attachment.format
        val depth = if (visibility == DepthMode.SeeThrough || target.depthAttachment == null)
            null else DepthState(depthCompare, true)

        DepthStencilState(format, depth, base.depthStencil?.stencil)
    }
}
