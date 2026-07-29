/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.MemoryStack

/**
 * Graphics backend used by a device implementation.
 */
enum class GraphicsBackend {
    OpenGL,
    Vulkan,
}

/**
 * Backend capabilities that affect which rendering paths may be created.
 */
data class GraphicsCapabilities(
    val supportsShaders: Boolean,
    val supportsRenderTargets: Boolean,
    val supportsRGBA16FloatRenderTargets: Boolean,
    val supportsNativeUniformBuffers: Boolean,
)

/**
 * Owns backend resources and creates command encoders.
 *
 * Shader source reaches the backend through [createShaderModule]. An OpenGL
 * implementation may ask the driver to compile GLSL, while a Vulkan
 * implementation may compile it to SPIR-V with shaderc.
 */
interface GraphicsDevice : AutoCloseable {
    val backend: GraphicsBackend
    val capabilities: GraphicsCapabilities
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    fun createShaderModule(description: ShaderModuleDescription): ShaderModule
    fun createRenderPipeline(description: RenderPipelineDescription): RenderPipeline
    fun createBuffer(description: BufferDescription): GpuBuffer
    fun createRenderTarget(description: RenderTargetDescription): TextureRenderTarget
    fun createSampler(description: SamplerDescription): GpuSampler
    fun createCommandEncoder(label: String): CommandEncoder

    /**
     * Resolves a protocol through this device's pipeline cache.
     *
     * The returned pipeline is borrowed from the device and must not be closed by the caller.
     */
    fun resolveRenderPipeline(protocol: RenderProtocol): RenderPipeline

    /**
     * Resolves an immutable sampler description through this device's resource cache.
     *
     * The returned sampler is borrowed from the device and must not be closed by the caller.
     */
    fun resolveSampler(description: SamplerDescription): GpuSampler
}

/**
 * Records one self-contained command sequence and always closes its encoder.
 */
inline fun GraphicsDevice.record(label: String, commands: CommandScope.() -> Unit) {
    createCommandEncoder(label).use(commands)
}
