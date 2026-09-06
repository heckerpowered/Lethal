/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.shader.ShaderStagesDescription
import heckerpowered.render.target.RenderAttachment
import heckerpowered.render.texture.GpuTextureView

/**
 * Owns backend resources and encodes graphics commands.
 *
 * Shader source reaches the backend through [createShaderModule]. An OpenGL
 * implementation may ask the driver to compile GLSL, while a Vulkan
 * implementation may compile it to SPIR-V with shaderc.
 */
interface GraphicsDevice {
    val memoryStack: MemoryStack
    val primitives: BuiltInPrimitives

    /**
     * Creates a [ShaderModule] for the stage and entry point selected by [description].
     *
     * Source code is compiled during module creation. Precompiled shader code does not require source
     * compilation, but may still be validated or translated before it can be used by this graphics
     * device.
     *
     * Creation fails if the shader representation is unsupported or invalid, source compilation
     * fails, the selected entry point does not exist, or the entry point cannot be used for the
     * requested stage. Reported failures include shader diagnostics when they are available.
     *
     * Successful creation establishes only that the module is valid in isolation. Compatibility with
     * other shader stages and with a complete render pipeline is established separately.
     */
    fun createShaderModule(description: ShaderModuleDescription): ShaderModule

    /**
     * Establishes the shader-stage combination described by [description].
     *
     * The returned [ShaderStages] may be reused when creating multiple compatible render pipelines.
     * This operation does not create a render pipeline or select vertex input, rasterization,
     * depth-stencil, blending, or attachment state.
     *
     * Every referenced shader module must be open and must have been created by this graphics device.
     * No two modules may declare the same [ShaderModule.stage]. If these requirements are violated, or
     * this device cannot establish the requested combination, creation fails and no [ShaderStages] is
     * returned.
     *
     * Successful creation does not establish compatibility with a particular pipeline layout, vertex
     * input, attachment configuration, or other fixed-function state. Those requirements are
     * established separately during render-pipeline creation.
     */
    fun createShaderStages(description: ShaderStagesDescription): ShaderStages

    fun createRenderPipeline(description: RenderPipelineDescription): RenderPipeline
    fun createBuffer(description: BufferDescription): GpuBuffer
    fun createRenderTarget(description: RenderTargetDescription): OwnedTextureRenderTarget
    fun createSampler(description: SamplerDescription): GpuSampler

    /**
     * Encodes one command sequence while this device owns the encoder's complete lifetime.
     */
    fun encode(label: String, commands: CommandEncoder.() -> Unit)

    /**
     * Resolves a description through this device's pipeline cache.
     *
     * The returned pipeline is borrowed from the device and must not be closed by the caller.
     */
    fun resolveRenderPipeline(description: RenderPipelineDescription): RenderPipeline

    /**
     * Resolves an immutable sampler description through this device's resource cache.
     *
     * The returned sampler is borrowed from the device and must not be closed by the caller.
     */
    fun resolveSampler(description: SamplerDescription): GpuSampler

    /**
     * Makes the texture subresources selected by [view] available as a render-pass attachment.
     *
     * For example, use the attachment to render scene color into a texture, then sample that texture
     * in a later post-processing pass.
     *
     * The attachment references the same image storage, format, and subresource selection as [view].
     * Creating it does not allocate independent image storage, copy texels, or expand the permitted
     * uses of the source.
     *
     * The view must select exactly one mip level. A broader view is rejected rather than narrowed,
     * and the selected array layers are preserved. The format and permitted uses must support
     * attachment access.
     *
     * The attachment can be reused across passes with different load and store operations.
     * Compatibility with other attachments and the render pipeline is checked when it is used.
     * Any access-scope restrictions on imported resources also apply to the returned attachment.
     *
     * @throws IllegalArgumentException if [view] does not belong to this graphics device or does not
     * select exactly one mip level.
     * @throws UnsupportedOperationException if attachment access is unsupported for the view's
     * format, subresource selection, or permitted uses.
     * @throws IllegalStateException if the view or its storage is no longer valid or is unavailable
     * in the current access scope.
     */
    fun createAttachmentView(view: GpuTextureView): RenderAttachment
}
