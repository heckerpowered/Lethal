/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.command.CommandEncoder
import heckerpowered.render.command.ImageRegion
import heckerpowered.render.command.TextureDataLayout
import heckerpowered.render.command.pass.AttachmentLoadOperation
import heckerpowered.render.command.pass.RenderPass
import heckerpowered.render.command.pass.RenderPassDescription
import heckerpowered.render.command.pass.RenderPassResources
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.resource.buffer.GpuBufferView
import heckerpowered.render.resource.texture.TextureAspect

/** Direct execution; the current pass identity is also the authority for escaped pass references. */
internal class OpenGLCommandEncoder(val device: OpenGLGraphicsDevice) : CommandEncoder {
    internal var currentPass: OpenGLClearPass? = null
        private set
    private var failed = false

    override val memoryStack: MemoryStack
        get() {
            checkActive()
            return device.memoryStack
        }

    fun checkActive() {
        device.checkAccess()
        check(device.currentEncoder === this) { "Command encoder is outside its encode scope" }
        check(!failed) { "The current encoding failed and cannot continue" }
    }

    fun checkOutsidePass() {
        checkActive()
        check(currentPass == null) { "An operation requiring no active render pass was called inside one" }
    }

    override fun renderPass(description: RenderPassDescription, resources: RenderPassResources, commands: RenderPass.() -> Unit) = context(device, device.functions) {
        checkOutsidePass()
        resources.validateFor(description)
        validateResources(resources)
        validateAttachments(description)
        val colorAttachment = checkNotNull(description.colorAttachments.single())
        val attachment = device.requireAttachment(colorAttachment.attachment)
        checkError("before render pass '${description.label}'")

        val pass = OpenGLClearPass(this@OpenGLCommandEncoder, description, attachment)
        withinPass(pass) {
            withColorFramebuffer(attachment) {
                when (val load = colorAttachment.operation.load) {
                    is AttachmentLoadOperation.Clear -> clearBoundColorAttachment(attachment, description.renderArea, load.value)
                    AttachmentLoadOperation.Load, AttachmentLoadOperation.Discard -> Unit
                }
                commands(pass)
                pass.checkActive()
                checkError("render pass '${description.label}'")
                // Store needs no command. Omitting discard invalidation does not enlarge the
                // undefined region or destroy texels outside renderArea.
            }
        }
    }

    private fun validateResources(resources: RenderPassResources) {
        val hasShaderResources = resources.descriptors.isNotEmpty()
        val hasGeometryResources = resources.vertexBuffers.isNotEmpty() || resources.indexBuffers.isNotEmpty()
        if (hasShaderResources || hasGeometryResources) {
            TODO("Prepare shader and geometry resources before entering the OpenGL pass")
        }
    }

    private fun validateAttachments(description: RenderPassDescription) {
        val hasDepthOrStencil = description.depthAttachment != null || description.stencilAttachment != null
        val hasResolves = description.colorResolves.isNotEmpty()
        val hasOneColorAttachment = description.colorAttachments.singleOrNull() != null
        val usesOneLayer = description.layerCount == 1
        val isSupportedPass = hasOneColorAttachment && usesOneLayer && !hasDepthOrStencil && !hasResolves
        if (!isSupportedPass) {
            throw UnsupportedOperationException("A single color attachment without depth, stencil, or resolve is required")
        }
    }

    /** An exceptional pass cannot be erased from a directly executed command sequence. */
    private inline fun withinPass(pass: OpenGLClearPass, operation: () -> Unit) {
        currentPass = pass
        var completed = false
        try {
            operation()
            completed = true
        } finally {
            currentPass = null
            if (!completed) failed = true
        }
    }

    override fun discardContents(region: ImageRegion) {
        checkOutsidePass()
        val texture = when (region) {
            is ImageRegion.Texture -> device.requireTexture(region.texture)
            is ImageRegion.View -> device.requireTextureView(region.view).texture
            is ImageRegion.Attachment -> device.requireAttachment(region.attachment).texture
        }
        val selectsColorStorage = region.aspect == TextureAspect.Color && region.format == texture.format
        require(selectsColorStorage) { "Only the color aspect is available" }
        // No native invalidation is necessary: discard revokes a content guarantee, not the
        // physical storage. In particular, never broaden a subrectangle to the whole mip.
    }

    override fun writeBuffer(destination: GpuBufferView, sourceAddress: NativeAddress): Unit = TODO("Implement OpenGL buffer upload")
    override fun copyBuffer(source: GpuBufferView, destination: GpuBufferView): Unit = TODO("Implement OpenGL buffer copy")
    override fun writeTexture(destination: ImageRegion, sourceAddress: NativeAddress, sourceLayout: TextureDataLayout): Unit = TODO("Implement OpenGL texture upload")
    override fun copyBufferToTexture(source: GpuBufferView, destination: ImageRegion, sourceLayout: TextureDataLayout): Unit = TODO("Implement OpenGL buffer-to-texture copy")
    override fun copyTextureToBuffer(source: ImageRegion, destination: GpuBufferView, destinationLayout: TextureDataLayout): Unit = TODO("Implement OpenGL texture-to-buffer copy")
    override fun copyTexture(source: ImageRegion, destination: ImageRegion): Unit = TODO("Implement OpenGL texture copy")
    override fun resolve(source: ImageRegion, destination: ImageRegion): Unit = TODO("Implement OpenGL resolve")
}
