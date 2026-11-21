/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.engine.postprocessing.tonemapping.aces

import heckerpowered.render.GraphicsDevice
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.engine.material.AlphaQuantity
import heckerpowered.render.engine.material.AlphaRepresentation
import heckerpowered.render.engine.material.parameter.TextureParameterValue
import heckerpowered.render.memory.directBufferAddress
import heckerpowered.render.resource.ResourceLifetime
import heckerpowered.render.resource.lifetime
import heckerpowered.render.resource.sampler.SamplerAddressMode
import heckerpowered.render.resource.sampler.TextureFilter
import heckerpowered.render.resource.sampler.createSampler
import heckerpowered.render.resource.texture.TextureFormat
import heckerpowered.render.resource.texture.TextureUsage
import heckerpowered.render.resource.texture.createTexture
import heckerpowered.render.resource.texture.createTextureView
import heckerpowered.render.shader.source.MemoryShaderSource
import heckerpowered.render.shader.source.ShaderSource
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An ACES 2.0 output transform: display configuration, generated shader parameters and
 * hue lookup data belong together. Tone mapping and visibility must borrow the same transform.
 * GPU resources belong to the lifetime passed to [create], not to this value.
 * Output is display-linear RGB in the configured limiting primaries, relative to 100 nits.
 * Display encoding and HDR presentation remain the caller's responsibility.
 */
class Aces20 private constructor(val configuration: Aces20Configuration, parameters: Aces20Parameters, internal val hueTexture: TextureParameterValue?) {
    internal val shaderSource: ShaderSource = MemoryShaderSource(parameters.shaderSource(hueTexture != null))

    /**
     * Creates ACES output transforms either with a prepared GPU hue lookup or with CPU-generated
     * values embedded in shader source. The supplied resource lifetime must cover GPU use of
     * uploaded lookup data.
     */
    companion object {
        /**
         * CPU-only default for renderers without a prepared texture; generated values live in the shader.
         */
        internal val Default: Aces20 by lazy {
            val configuration = Aces20Configuration()
            Aces20(configuration, Aces20Parameters.generate(configuration), null)
        }

        /**
         * Generates this configuration once and uploads its lookup data. The owner must retain
         * [lifetime] through submitted work and establish GPU completion before closing it,
         * including if preparation fails. No Python, external generator or binary asset is used.
         */
        fun create(device: GraphicsDevice, lifetime: ResourceLifetime, configuration: Aces20Configuration = Aces20Configuration()): Aces20 {
            val parameters = Aces20Parameters.generate(configuration)
            val pixels = parameters.huePixels()
            val texture = device.createTexture("ACES hue table", Aces20HueLayout.TEXTURE_WIDTH, 1, TextureFormat.Rgba32Float, TextureUsage.Sampled, TextureUsage.TransferDestination).lifetime(lifetime)
            val view = device.createTextureView(texture, "ACES hue table")
            val sampler = device.createSampler(TextureFilter.Nearest, TextureFilter.Nearest, SamplerAddressMode.ClampToEdge, SamplerAddressMode.ClampToEdge, lifetime = lifetime)
            val sampled = TextureParameterValue(view, sampler, AlphaRepresentation.Premultiplied, AlphaQuantity.Signal)

            device.encode("ACES hue table upload") {
                writeTexture(ImageRegion.Texture(texture), directBufferAddress(pixels))
            }
            device.awaitIdle()
            return Aces20(configuration, parameters, sampled)
        }
    }
}

private fun Aces20Parameters.huePixels(): ByteBuffer {
    val lookup = gamut.lookup
    val pixels = ByteBuffer
        .allocateDirect(Aces20HueLayout.TEXTURE_WIDTH * Aces20HueLayout.BYTES_PER_TEXEL)
        .order(ByteOrder.nativeOrder())
    for (index in 0 until Aces20HueLayout.TEXEL_COUNT) {
        pixels.putFloat(lookup.cusp(index, 0))
        pixels.putFloat(lookup.cusp(index, 1))
        pixels.putFloat(lookup.cusp(index, 2))
        pixels.putFloat(lookup.reach(index))
    }
    pixels.rewind()
    return pixels
}
