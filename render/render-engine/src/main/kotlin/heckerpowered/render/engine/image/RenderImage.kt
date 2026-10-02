/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.image

import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.texture.GpuTextureView

/**
 * Makes one effect image available both as a render destination and as a sampled input.
 *
 * [attachment] and [view] must describe the same image region, with [size] matching its dimensions.
 * Images created by [RenderImageStore] stay valid until that allocation is released by the store.
 * The sampler is supplied externally and must remain valid for every use; this value does not
 * register or close any resources itself.
 */
class RenderImage(
    val view: GpuTextureView,
    val attachment: RenderAttachment,
    val size: ImageSize,
    val sampler: GpuSampler,
    val alphaQuantity: AlphaQuantity = AlphaQuantity.Coverage,
) {
    /**
     * Returns this [view] and [sampler] as a [TextureParameterValue] for sampled shader input,
     * marked [AlphaRepresentation.Premultiplied].
     *
     * For ordinary alpha-bearing color, the caller must supply RGB already multiplied by alpha.
     * [RenderImage] itself does not guarantee that representation; this method only wraps the
     * references and declares it, without converting or inspecting texels.
     *
     * [AlphaQuantity.Signal] distinguishes additive glow from ordinary coverage. Its RGB is consumed
     * without another source-alpha factor and may remain nonzero at zero alpha. Producer and consumer
     * must agree on that use; the meaning is preserved here without inspecting existing contents.
     */
    fun sampled() = TextureParameterValue(view, sampler, AlphaRepresentation.Premultiplied, alphaQuantity)
}
