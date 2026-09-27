/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.BuiltInPrimitives
import heckerpowered.render.GraphicsDevice
import heckerpowered.render.RenderPipelineDescription
import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.opengl.function.*
import heckerpowered.render.opengl.shader.OpenGLShaderCompiler
import heckerpowered.render.pipeline.PipelineLayout
import heckerpowered.render.pipeline.PipelineLayoutDescription
import heckerpowered.render.pipeline.RenderPipeline
import heckerpowered.render.resource.buffer.BufferDescription
import heckerpowered.render.resource.buffer.GpuBuffer
import heckerpowered.render.resource.sampler.GpuSampler
import heckerpowered.render.resource.sampler.SamplerDescription
import heckerpowered.render.resource.target.RenderAttachment
import heckerpowered.render.resource.target.validateAttachmentView
import heckerpowered.render.resource.texture.*
import heckerpowered.render.shader.ShaderModule
import heckerpowered.render.shader.ShaderModuleDescription
import heckerpowered.render.shader.ShaderStages
import heckerpowered.render.shader.ShaderStagesDescription

/**
 * Creates buffer and shader resources and executes uploads and attachment-only passes in an existing OpenGL context.
 *
 * This first implementation supports backed, single-sampled RGBA8 images with one two-dimensional
 * mip and one layer. Views and attachments refer to that same image. A pass can load, clear, or
 * discard it. Buffer creation and host uploads are also implemented, as are shader compilation
 * and linking. Drawing, GPU copies, image transfers, depth/stencil, resolves, resource binding,
 * and imported host targets are not implemented here yet. Those entry points reject requests
 * instead of silently ignoring them. This is not yet a complete drawing backend.
 *
 * [functions] comes from the host's LWJGL adapter. The host keeps that context current on its
 * original thread and owns its lifetime. No window, context, command replay list, or swap operation
 * is created. Images returned by this device are independently closeable; this device has no
 * persistent native objects of its own. [memoryStack] may be shared with the host on that thread.
 *
 * Calls must be made outside native begin/end, conditional-rendering, and active transform-feedback
 * scopes. Application callbacks must not call unrelated native GL code or switch contexts. This
 * implementation restores only the bindings and clear state it changes; it cannot preserve an
 * arbitrary native protocol that surrounds those calls. The GL error state must be clean on entry;
 * a pending error is reported rather than silently discarded or attributed to a new operation.
 *
 * Commands execute directly. An exception cannot roll back a preceding clear. Normal encode exit
 * flushes pending GL commands without waiting for GPU completion. Object deletion relies on GL's
 * own in-flight object retention, not on a new RHI retirement queue.
 */
class OpenGLGraphicsDevice(
    internal val functions: OpenGLFunctions,
    override val memoryStack: MemoryStack = MemoryStack(),
) : GraphicsDevice {
    internal var currentEncoder: OpenGLCommandEncoder? = null
        private set

    init {
        context(functions) {
            checkAccess()
            if (framebuffers == null) {
                throw UnsupportedOperationException("Offscreen rendering requires framebuffer objects")
            }
            checkError("device creation")
        }
    }

    internal fun checkAccess() = context(functions) { checkCurrentContext() }

    override fun createTexture(description: TextureDescription): GpuTexture = context(this) {
        try {
            OpenGLTexture.create(description)
        } catch (failure: OpenGLOperationException) {
            throw TextureCreationException("OpenGL could not create texture '${description.label}'", failure)
        }
    }

    override fun createTextureView(texture: GpuTexture, description: TextureViewDescription): GpuTextureView {
        checkAccess()
        val image = requireTexture(texture)
        description.validateFor(image)
        if (description.dimension != TextureViewDimension.TwoDimensional) {
            throw UnsupportedOperationException("This implementation exposes two-dimensional views only")
        }
        return OpenGLTextureView(image, description)
    }

    override fun createAttachmentView(view: GpuTextureView): RenderAttachment {
        checkAccess()
        val source = requireTextureView(view)
        validateAttachmentView(source)
        return OpenGLRenderAttachment(source)
    }

    internal fun requireBuffer(buffer: GpuBuffer): OpenGLBuffer {
        require(buffer is OpenGLBuffer) { "Buffer was not created by this OpenGL backend" }
        val belongsToDevice = buffer.device === this
        require(belongsToDevice) { "Buffer was not created by this graphics device" }
        buffer.checkOpen()
        return buffer
    }

    internal fun requireTexture(texture: GpuTexture): OpenGLTexture {
        require(texture is OpenGLTexture) { "Texture was not created by this OpenGL backend" }
        val belongsToDevice = texture.device === this
        require(belongsToDevice) { "Texture was not created by this graphics device" }
        texture.checkOpen()
        return texture
    }

    internal fun requireTextureView(view: GpuTextureView): OpenGLTextureView {
        require(view is OpenGLTextureView) { "Texture view was not created by this OpenGL backend" }
        requireTexture(view.texture)
        return view
    }

    internal fun requireAttachment(attachment: RenderAttachment): OpenGLRenderAttachment {
        require(attachment is OpenGLRenderAttachment) { "Attachment was not created by this OpenGL backend" }
        requireTextureView(attachment.view)
        return attachment
    }

    internal fun isActiveAttachment(texture: OpenGLTexture): Boolean {
        val pass = currentEncoder?.currentPass ?: return false
        return pass.attachment.texture === texture
    }

    override fun encode(label: String, commands: CommandEncoder.() -> Unit) = context(functions) {
        checkAccess()
        check(currentEncoder == null) { "Nested encode on the same device is not supported" }
        checkError("before encode '$label'")

        memoryStack.frame {
            val encoder = OpenGLCommandEncoder(this@OpenGLGraphicsDevice)
            currentEncoder = encoder
            try {
                commands(encoder)
                encoder.checkOutsidePass()
                flush()
                checkError("encode '$label'")
            } finally {
                currentEncoder = null
            }
        }
    }

    override val primitives: BuiltInPrimitives get() = TODO("Implement OpenGL built-in geometry")
    override fun createShaderModule(description: ShaderModuleDescription): ShaderModule = context(this) { OpenGLShaderCompiler.compile(description) }
    override fun createShaderStages(description: ShaderStagesDescription): ShaderStages = context(this) { OpenGLShaderCompiler.link(description) }
    override fun createPipelineLayout(description: PipelineLayoutDescription): PipelineLayout = TODO("Implement OpenGL pipeline layouts")
    override fun createRenderPipeline(description: RenderPipelineDescription): RenderPipeline = TODO("Implement OpenGL render pipelines")
    override fun createBuffer(description: BufferDescription): GpuBuffer = context(this) { OpenGLBuffer.create(description) }
    override fun createSampler(description: SamplerDescription): GpuSampler = TODO("Implement OpenGL samplers")
    override fun resolveRenderPipeline(description: RenderPipelineDescription): RenderPipeline = createRenderPipeline(description)
    override fun resolveSampler(description: SamplerDescription): GpuSampler = createSampler(description)
}

/** A native error observed at a checked operation boundary; no retry or rollback is implied. */
class OpenGLOperationException(val errorCode: Int, operation: String) :
    IllegalStateException("OpenGL error 0x${errorCode.toUInt().toString(16)}: $operation")

context(_: OpenGLStateFunctions)
internal fun checkError(operation: String) {
    val error = getError()
    if (error != 0) {
        throw OpenGLOperationException(error, operation)
    }
}
