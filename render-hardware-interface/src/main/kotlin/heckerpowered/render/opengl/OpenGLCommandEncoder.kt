/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.*
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import org.lwjgl.opengl.*

internal class OpenGLCommandEncoder(
    private val label: String,
    val graphicsDevice: OpenGLGraphicsDevice,
    private val binding: OpenGLBinding,
    framebuffers: OpenGLFramebuffers,
    override val memoryStack: MemoryStack,
    private val uniformFrame: OpenGLUniformArena.Frame,
) : CommandEncoder {
    private val hostState = OpenGLHostState.capture(framebuffers)
    private val sampledTextureParameters = mutableMapOf<Int, OpenGLTextureParameters>()
    private val textureUnitBindings = mutableMapOf<Int, Int>()
    private val uniformBufferBindings = mutableMapOf<Int, Int>()
    private var activePass: OpenGLRenderPass? = null
    private var isClosed = false

    init {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)
    }

    override fun writeBuffer(buffer: GpuBuffer, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int) {
        requireOpen()
        val openGLBuffer = buffer as? OpenGLBuffer ?: error("$label cannot write a buffer created by another graphics backend")
        openGLBuffer.write(memoryStack, sourceAddress, sizeBytes, destinationOffsetBytes)
    }

    override fun writeUniform(layout: UniformBufferLayout, sourceAddress: NativeAddress): UniformBinding {
        requireOpen()
        return uniformFrame.write(layout, memoryStack, sourceAddress)
    }

    override fun renderPass(description: RenderPassDescription, encode: RenderPass.() -> Unit) {
        requireOpen()
        check(activePass == null) { "$label cannot begin a render pass while another pass is active" }
        val target = description.target as? OpenGLRenderTarget ?: error("${description.label} requires an OpenGL render target")
        val pass = OpenGLRenderPass(this, description, target)
        activePass = pass
        try {
            pass.encode()
        } finally {
            pass.close()
            activePass = null
        }
    }

    override fun close() {
        if (isClosed) return
        try {
            activePass?.close()
            restoreSampledTextures()
            restoreUniformBuffers()
            GL11.glPopClientAttrib()
            GL11.glPopAttrib()
            hostState.restore()
        } finally {
            uniformFrame.close()
            isClosed = true
        }
    }

    fun bindTexture(texture: OpenGLTexture, sampler: OpenGLSampler, textureUnitIndex: Int) {
        val textureUnit = GL13.GL_TEXTURE0 + textureUnitIndex
        GL13.glActiveTexture(textureUnit)
        textureUnitBindings.getOrPut(textureUnit) { GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.textureIdentifier)
        sampledTextureParameters.getOrPut(texture.textureIdentifier, OpenGLTextureParameters::capture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, sampler.description.minificationFilter.openGLIdentifier)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, sampler.description.magnificationFilter.openGLIdentifier)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, sampler.description.horizontalAddressMode.openGLIdentifier)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, sampler.description.verticalAddressMode.openGLIdentifier)
    }

    fun bindUniformBuffer(buffer: OpenGLBuffer, bindingPoint: Int) {
        uniformBufferBindings.getOrPut(bindingPoint) { binding.getIndexedInteger(GL31.GL_UNIFORM_BUFFER_BINDING, bindingPoint) }
        val previousGenericBinding = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING)
        try {
            binding.bindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, buffer.identifier())
        } finally {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previousGenericBinding)
        }
    }

    fun withOpenGLInterop(commands: () -> Unit) {
        requireOpen()
        check(activePass == null) { "$label cannot begin OpenGL interoperation while a render pass is active" }
        commands()
    }

    fun requireUniformBinding(uniform: UniformBinding): OpenGLUniformBinding = uniformFrame.requireBinding(uniform)

    private fun restoreSampledTextures() {
        for ((textureIdentifier, parameters) in sampledTextureParameters) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureIdentifier)
            parameters.restore()
        }
        for ((textureUnit, textureIdentifier) in textureUnitBindings) {
            GL13.glActiveTexture(textureUnit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureIdentifier)
        }
    }

    private fun restoreUniformBuffers() {
        if (uniformBufferBindings.isEmpty()) return

        val previousGenericBinding = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING)
        try {
            for ((bindingPoint, bufferIdentifier) in uniformBufferBindings) binding.bindBufferBase(GL31.GL_UNIFORM_BUFFER, bindingPoint, bufferIdentifier)
        } finally {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previousGenericBinding)
        }
    }

    private fun requireOpen() {
        check(!isClosed) { "$label command encoder has already been closed" }
    }
}

private class OpenGLRenderPass(
    private val encoder: OpenGLCommandEncoder,
    private val description: RenderPassDescription,
    private val target: OpenGLRenderTarget,
) : RenderPass {
    override val memoryStack: MemoryStack
        get() = encoder.memoryStack
    override val primitives
        get() = encoder.graphicsDevice.primitives

    private var pipeline: OpenGLRenderPipeline? = null
    private val vertexBuffers = mutableMapOf<Int, OpenGLVertexBufferBinding>()
    private val enabledVertexAttributes = mutableSetOf<Int>()
    private val textureUnits = mutableMapOf<TextureDescriptorLocation, Int>()
    private var isClosed = false

    init {
        target.bind()
        clearAttachments()
    }

    override fun bindProtocol(protocol: RenderProtocol) {
        bindPipeline(encoder.graphicsDevice.resolveRenderPipeline(protocol))
    }

    override fun bindPipeline(pipeline: RenderPipeline) {
        requireOpen()
        val openGLPipeline = pipeline as? OpenGLRenderPipeline ?: error("${description.label} requires an OpenGL render pipeline")
        this.pipeline = openGLPipeline
        openGLPipeline.bind()
        configureVertexAttributes()
    }

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Int) {
        requireOpen()
        require(slot >= 0) { "Vertex buffer slot must be non-negative" }
        require(offsetBytes >= 0) { "Vertex buffer offset must be non-negative" }
        pipeline?.let { require(slot in it.vertexBufferLayout.bindings.indices) { "${description.label} pipeline does not declare vertex buffer slot $slot" } }
        val openGLBuffer = buffer as? OpenGLBuffer ?: error("${description.label} requires an OpenGL vertex buffer")
        vertexBuffers[slot] = OpenGLVertexBufferBinding(openGLBuffer, offsetBytes)
        configureVertexAttributes()
    }

    override fun bindDescriptorSet(set: Int, descriptors: DescriptorSet) {
        requireOpen()
        val currentPipeline = requirePipeline()
        val expectedLayout = currentPipeline.descriptorSetLayouts.getOrNull(set) ?: error("${description.label} pipeline does not declare descriptor set $set")
        require(descriptors.layout == expectedLayout) { "${description.label} descriptor set $set does not match the pipeline layout" }

        for (layoutBinding in expectedLayout.bindings) {
            when (layoutBinding) {
                is DescriptorBinding.UniformBuffer -> bindUniformBuffer(currentPipeline, set, layoutBinding, descriptors.descriptor(layoutBinding.binding))
                is DescriptorBinding.CombinedImageSampler -> bindCombinedImageSampler(currentPipeline, set, layoutBinding, descriptors.descriptor(layoutBinding.binding))
            }
        }
    }

    override fun pushConstants(stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int) {
        requirePipeline().pushConstants(memoryStack, stages, sourceAddress, sizeBytes, destinationOffsetBytes)
    }

    override fun draw(vertexCount: Int, firstVertex: Int) {
        requireOpen()
        require(vertexCount > 0) { "Draw vertex count must be positive" }
        require(firstVertex >= 0) { "First vertex must be non-negative" }
        val currentPipeline = requirePipeline()
        val missingSlots = currentPipeline.vertexBufferLayout.bindings.indices.filterNot(vertexBuffers::containsKey)
        check(missingSlots.isEmpty()) { "${description.label} must bind vertex buffer slots ${missingSlots.joinToString()} before drawing" }
        GL11.glDrawArrays(currentPipeline.primitiveTopology.openGLIdentifier, firstVertex, vertexCount)
    }

    fun close() {
        if (isClosed) return
        enabledVertexAttributes.forEach(GL20::glDisableVertexAttribArray)
        enabledVertexAttributes.clear()
        isClosed = true
    }

    private fun clearAttachments() {
        var clearMask = 0
        if (description.colorLoadOperation == AttachmentLoadOperation.Clear) {
            val clearColor = description.clearColor
            GL11.glColorMask(true, true, true, true)
            GL11.glClearColor(clearColor.red, clearColor.green, clearColor.blue, clearColor.alpha)
            clearMask = clearMask or GL11.GL_COLOR_BUFFER_BIT
        }
        if (target.hasDepthAttachment && description.depthLoadOperation == AttachmentLoadOperation.Clear) {
            GL11.glDepthMask(true)
            GL11.glClearDepth(description.clearDepth)
            clearMask = clearMask or GL11.GL_DEPTH_BUFFER_BIT
        }
        if (clearMask != 0) GL11.glClear(clearMask)
    }

    private fun configureVertexAttributes() {
        val currentPipeline = pipeline ?: return
        enabledVertexAttributes.forEach(GL20::glDisableVertexAttribArray)
        enabledVertexAttributes.clear()

        for ((slot, bindingLayout) in currentPipeline.vertexBufferLayout.bindings.withIndex()) {
            val binding = vertexBuffers[slot] ?: continue
            binding.buffer.bindVertexBuffer()
            for (attribute in bindingLayout.attributes) {
                GL20.glEnableVertexAttribArray(attribute.location)
                GL20.glVertexAttribPointer(attribute.location, attribute.format.componentCount, GL11.GL_FLOAT, false, bindingLayout.strideBytes, (binding.offsetBytes + attribute.offsetBytes).toLong())
                enabledVertexAttributes += attribute.location
            }
        }
    }

    private fun bindUniformBuffer(pipeline: OpenGLRenderPipeline, set: Int, layout: DescriptorBinding.UniformBuffer, descriptor: Descriptor) {
        val uniformBuffer = descriptor as? Descriptor.UniformBuffer ?: error("${description.label} descriptor $set:${layout.binding} is not a uniform buffer")
        val openGLBuffer = encoder.requireUniformBinding(uniformBuffer.uniform).buffer
        val bindingPoint = pipeline.uniformBufferBindingPoint(set, layout.binding)
        if (bindingPoint != null) {
            encoder.bindUniformBuffer(openGLBuffer, bindingPoint)
            return
        }

        pipeline.uploadUniformBuffer(memoryStack, set, layout, openGLBuffer, 0, openGLBuffer.sizeBytes)
    }

    private fun bindCombinedImageSampler(pipeline: OpenGLRenderPipeline, set: Int, layout: DescriptorBinding.CombinedImageSampler, descriptor: Descriptor) {
        val imageSampler = descriptor as? Descriptor.CombinedImageSampler ?: error("${description.label} descriptor $set:${layout.binding} is not a combined image sampler")
        val texture = imageSampler.texture as? OpenGLTexture ?: error("${description.label} requires an OpenGL texture")
        val sampler = encoder.graphicsDevice.resolveSampler(imageSampler.sampler) as OpenGLSampler
        require(target !is OpenGLTextureRenderTarget || target.colorTexture.textureIdentifier != texture.textureIdentifier) { "${description.label} cannot sample from its color attachment" }

        val location = TextureDescriptorLocation(set, layout.binding)
        val textureUnit = textureUnits.getOrPut(location) { textureUnits.size }
        encoder.bindTexture(texture, sampler, textureUnit)
        GL20.glUniform1i(pipeline.samplerLocation(set, layout.binding), textureUnit)
    }

    private fun requirePipeline(): OpenGLRenderPipeline {
        requireOpen()
        return requireNotNull(pipeline) { "${description.label} must bind a pipeline before recording resource commands" }
    }

    private fun requireOpen() {
        check(!isClosed) { "${description.label} render pass has already been closed" }
    }
}

private fun DescriptorSet.descriptor(binding: Int): Descriptor {
    return descriptors.single { it.binding == binding }
}

private data class TextureDescriptorLocation(val set: Int, val binding: Int)

private data class OpenGLVertexBufferBinding(val buffer: OpenGLBuffer, val offsetBytes: Int)

private data class OpenGLTextureParameters(
    private val minificationFilter: Int,
    private val magnificationFilter: Int,
    private val horizontalAddressMode: Int,
    private val verticalAddressMode: Int,
) {
    fun restore() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minificationFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magnificationFilter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, horizontalAddressMode)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, verticalAddressMode)
    }

    companion object {
        fun capture(): OpenGLTextureParameters {
            return OpenGLTextureParameters(
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T),
            )
        }
    }
}

private data class OpenGLHostState(
    private val framebuffers: OpenGLFramebuffers,
    private val framebufferIdentifier: Int,
    private val renderbufferIdentifier: Int,
    private val programIdentifier: Int,
    private val activeTexture: Int,
    private val activeTextureBinding: Int,
    private val arrayBufferIdentifier: Int,
    private val elementArrayBufferIdentifier: Int,
) {
    fun restore() {
        framebuffers.bindFramebuffer(framebufferIdentifier)
        framebuffers.bindRenderbuffer(renderbufferIdentifier)
        GL20.glUseProgram(programIdentifier)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferIdentifier)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferIdentifier)
        GL13.glActiveTexture(activeTexture)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, activeTextureBinding)
    }

    companion object {
        fun capture(framebuffers: OpenGLFramebuffers): OpenGLHostState {
            val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
            return OpenGLHostState(
                framebuffers,
                GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                activeTexture,
                GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
            )
        }
    }
}
