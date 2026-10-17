/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.pass

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
import heckerpowered.render.resource.texture.TextureFormat

/**
 * Resolves a fragment output's alpha quantity when it depends on a sampled texture.
 *
 * The shader may declare its output's alpha meaning directly or derive it from a named
 * texture parameter. Resolution replaces that dependency with the texture's declared
 * alpha quantity without changing the shader's output representation.
 */
internal fun resolvedFragmentOutput(outputs: Map<Int, FragmentOutput>, slot: Int, values: ParameterValues): FragmentOutput? {
    val declaration = outputs[slot] ?: return null
    val name = declaration.alphaFromTexture ?: return declaration
    val texture = values.require(name) as? TextureParameterValue ?: error("Output $slot derives alpha meaning from a texture value")

    return declaration.copy(alphaQuantity = texture.sampledAlphaQuantity, alphaFromTexture = null)
}

/**
 * Resolves the color target states for a geometry element within a raster pass.
 *
 * Each active attachment receives a state derived from its format, the corresponding shader
 * output, and the element's compositing mode. Missing attachments retain their positions
 * but disable color writes.
 *
 * Targets without stored alpha use implicit coverage. The resulting states must satisfy
 * the destination's color-content contract before they can be used by a pipeline.
 */
internal fun resolveColorTargets(element: GeometryElement, target: RenderPassDescription, outputs: Map<Int, FragmentOutput>, values: ParameterValues, destinations: Map<Int, AlphaQuantity>): List<ColorTargetState> {
    require(element.compositions.keys.all { target.colorAttachments.getOrNull(it) != null }) { "Composition location requires a present color attachment" }

    val states = target.colorAttachments.mapIndexed { slot, attachment ->
        if (attachment == null) return@mapIndexed ColorTargetState(TextureFormat.Rgba8UnsignedNormalized, writeMask = ColorWriteMask.None)

        val format = attachment.attachment.format
        val source = resolvedFragmentOutput(outputs, slot, values)
        val destination = if (format.colorComponents.alpha) destinations[slot] else AlphaQuantity.Coverage
        val composition = element.compositions[slot] ?: CompositingMode.Replace

        val state = composition.target(format, source, destination)
        require(state.format == format) { "Composition returned a different format at location $slot" }
        state
    }.toUnmodifiableList()

    validateColorContent(outputs, values, states, destinations, element.compositions)
    return states
}

/**
 * Resolves the depth and stencil state for a geometry element within a raster pass.
 *
 * Explicit depth/stencil declarations take precedence over attachment-derived defaults.
 * View-dependent depth comparison and depth-write overrides are applied afterward without
 * changing the selected stencil configuration.
 *
 * See-through drawing disables implicit depth processing and cannot explicitly enable
 * depth processing or request depth writes. Always-on-top drawing requires a separate depth phase.
 * A pass with only a stencil attachment may still have a depth/stencil state without depth processing.
 */
internal fun resolveDepthStencil(element: GeometryElement, target: RenderPassDescription, visibility: DepthMode, depthCompare: CompareFunction): DepthStencilState? {
    require(visibility != DepthMode.AlwaysOnTop) { "AlwaysOnTop requires a separate depth phase" }

    val declared = element.depthStencil
    validateDeclaredDepthStencil(declared, target, visibility)

    val selected = declared ?: defaultDepthStencil(target, visibility, depthCompare)
    val depth = selected?.depth
    val depthWrite = element.depthWrite

    require(depthWrite != true || visibility != DepthMode.SeeThrough) { "SeeThrough cannot write depth" }
    require(depthWrite != true || depth != null) { "Depth writes require active depth processing and an attachment" }

    if (depth == null) return selected
    if (!element.usesViewDepthCompare && depthWrite == null) return selected

    val compareFunction = if (element.usesViewDepthCompare) depthCompare else depth.compareFunction
    val writeEnabled = depthWrite ?: depth.writeEnabled

    return selected.copy(depth = depth.copy(compareFunction = compareFunction, writeEnabled = writeEnabled))
}

private fun defaultDepthStencil(target: RenderPassDescription, visibility: DepthMode, depthCompare: CompareFunction): DepthStencilState? {
    val attachment = (target.depthAttachment ?: target.stencilAttachment)?.attachment ?: return null
    val depth = if (visibility == DepthMode.SeeThrough || target.depthAttachment == null) null else DepthState(depthCompare, true)

    return DepthStencilState(attachment.format, depth)
}

private fun validateDeclaredDepthStencil(declared: DepthStencilState?, target: RenderPassDescription, visibility: DepthMode) {
    if (declared == null) return

    require((target.depthAttachment ?: target.stencilAttachment)?.attachment?.format == declared.format) { "Depth/stencil format mismatch" }
    require(declared.depth == null || target.depthAttachment != null) { "Depth processing requires an attachment" }
    require(declared.stencil == null || target.stencilAttachment != null) { "Stencil processing requires an attachment" }
    require(visibility != DepthMode.SeeThrough || declared.depth == null) { "SeeThrough cannot enable depth processing" }
}