/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.*
import heckerpowered.render.memory.MemoryStack
import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.memory.alloc
import heckerpowered.render.memory.floats
import org.lwjgl.opengl.*

internal class OpenGLRenderPipeline(
    description: RenderPipelineDescription,
    private val binding: OpenGLBinding,
    private val capabilities: OpenGLContextCapabilities,
) : RenderPipeline {
    val vertexBufferLayout: VertexBufferLayout = description.vertexBufferLayout
    val primitiveTopology = description.primitiveTopology
    val descriptorSetLayouts: List<DescriptorSetLayout> = description.descriptorSetLayouts

    private val blendState = description.blendState
    private val depthState = description.depthState
    private val cullMode = description.cullMode
    private val pushConstantLayout: PushConstantLayout? = description.pushConstants
    private val uniformLocations = mutableMapOf<String, Int>()
    private val emulatedUniformBuffers = mutableMapOf<DescriptorLocation, EmulatedUniformBufferState>()

    init {
        require(vertexBufferLayout.bindings.all { it.inputRate == VertexInputRate.Vertex }) { "OpenGL backend does not yet support instance-rate vertex buffers" }
    }

    private var programIdentifier = link(description)
    private val uniformBufferBindingPoints = configureUniformBufferBindings()

    fun bind() {
        check(programIdentifier >= 0) { "Render pipeline has already been closed" }
        GL20.glUseProgram(programIdentifier)
        applyBlendState(blendState)
        applyDepthState(depthState)
        applyCullMode(cullMode)

        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP)
        GL11.glColorMask(true, true, true, true)
        if (capabilities.supportsUnclampedColorOutput) {
            ARBColorBufferFloat.glClampColorARB(ARBColorBufferFloat.GL_CLAMP_VERTEX_COLOR_ARB, GL11.GL_FALSE)
            ARBColorBufferFloat.glClampColorARB(ARBColorBufferFloat.GL_CLAMP_FRAGMENT_COLOR_ARB, ARBColorBufferFloat.GL_FIXED_ONLY_ARB)
        }
    }

    fun descriptorBinding(set: Int, binding: Int): DescriptorBinding {
        val layout = descriptorSetLayouts.getOrNull(set) ?: error("Render pipeline does not declare descriptor set $set")
        return layout.bindings.singleOrNull { it.binding == binding } ?: error("Render pipeline descriptor set $set does not declare binding $binding")
    }

    fun uniformBufferBindingPoint(set: Int, binding: Int): Int? = uniformBufferBindingPoints[DescriptorLocation(set, binding)]

    fun samplerLocation(set: Int, binding: Int): Int {
        val descriptor = descriptorBinding(set, binding) as? DescriptorBinding.CombinedImageSampler ?: error("Descriptor $set:$binding is not a combined image sampler")
        return uniformLocation(descriptor.samplerName)
    }

    fun uploadUniformBuffer(memoryStack: MemoryStack, set: Int, descriptor: DescriptorBinding.UniformBuffer, buffer: OpenGLBuffer, offsetBytes: Int, sizeBytes: Int) {
        val location = DescriptorLocation(set, descriptor.binding)
        val state = EmulatedUniformBufferState(buffer, buffer.uniformRevision, offsetBytes, sizeBytes)
        if (emulatedUniformBuffers[location] == state) return

        require(sizeBytes >= descriptor.layout.sizeBytes) { "Uniform buffer data is smaller than its declared layout" }
        descriptor.layout.fields.forEach { upload(it, buffer, offsetBytes + it.offsetBytes, memoryStack) }
        emulatedUniformBuffers[location] = state
    }

    fun pushConstants(memoryStack: MemoryStack, stages: Set<ShaderStage>, sourceAddress: NativeAddress, sizeBytes: Int, destinationOffsetBytes: Int) {
        val layout = requireNotNull(pushConstantLayout) { "Render pipeline does not declare push constants" }
        val endOffsetBytes = destinationOffsetBytes + sizeBytes
        require(stages.isNotEmpty()) { "Push constant write requires at least one shader stage" }
        require(destinationOffsetBytes >= 0 && destinationOffsetBytes % Int.SIZE_BYTES == 0) { "Push constant offset must be non-negative and four-byte aligned" }
        require(sizeBytes > 0 && sizeBytes % Int.SIZE_BYTES == 0 && endOffsetBytes <= layout.sizeBytes) { "Push constant write exceeds its layout or is not four-byte aligned" }

        var wroteField = false
        for (field in layout.fields) {
            if (field.stages.none { it in stages }) continue
            val fieldEndOffsetBytes = field.offsetBytes + field.type.sizeBytes
            val overlapsWrite = field.offsetBytes < endOffsetBytes && fieldEndOffsetBytes > destinationOffsetBytes
            if (!overlapsWrite) continue

            require(field.offsetBytes >= destinationOffsetBytes && fieldEndOffsetBytes <= endOffsetBytes) { "Push constant write partially overlaps ${field.name}" }
            upload(field, memoryStack, sourceAddress + (field.offsetBytes - destinationOffsetBytes))
            wroteField = true
        }
        require(wroteField) { "Push constant write does not cover a field visible to the requested shader stages" }
    }

    override fun close() {
        if (programIdentifier < 0) return
        GL20.glDeleteProgram(programIdentifier)
        programIdentifier = -1
        uniformLocations.clear()
        emulatedUniformBuffers.clear()
    }

    private fun configureUniformBufferBindings(): Map<DescriptorLocation, Int> {
        if (!capabilities.supportsUniformBuffers) return emptyMap()

        val maximumBindings = GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS)
        val bindingPoints = mutableMapOf<DescriptorLocation, Int>()
        for ((setIndex, setLayout) in descriptorSetLayouts.withIndex()) {
            for ((binding, blockName) in setLayout.bindings.filterIsInstance<DescriptorBinding.UniformBuffer>()) {
                val blockIndex = this.binding.getUniformBlockIndex(programIdentifier, blockName)
                if (blockIndex == GL31.GL_INVALID_INDEX) continue

                val bindingPoint = bindingPoints.size
                require(bindingPoint < maximumBindings) { "Render pipeline requires more uniform buffer bindings than the OpenGL context exposes" }
                this.binding.uniformBlockBinding(programIdentifier, blockIndex, bindingPoint)
                bindingPoints[DescriptorLocation(setIndex, binding)] = bindingPoint
            }
        }
        return bindingPoints
    }

    private fun upload(field: ShaderField, buffer: OpenGLBuffer, sourceOffsetBytes: Int, memoryStack: MemoryStack) {
        val location = uniformLocation(field.name)
        when (field.type) {
            ShaderValueType.Int -> GL20.glUniform1i(location, buffer.uniformInt(sourceOffsetBytes))
            ShaderValueType.Float -> GL20.glUniform1f(location, buffer.uniformFloat(sourceOffsetBytes))
            ShaderValueType.Float2 -> GL20.glUniform2f(location, buffer.uniformFloat(sourceOffsetBytes), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES))
            ShaderValueType.Float3 -> GL20.glUniform3f(location, buffer.uniformFloat(sourceOffsetBytes), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES * 2))
            ShaderValueType.Float4 -> GL20.glUniform4f(location, buffer.uniformFloat(sourceOffsetBytes), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES * 2), buffer.uniformFloat(sourceOffsetBytes + Float.SIZE_BYTES * 3))
            ShaderValueType.Matrix4 -> memoryStack.alloc(floats(16)) { values ->
                buffer.copyUniformFloats(sourceOffsetBytes, 16, this, values)
                binding.uniformMatrix4(location, false, values)
            }
        }
    }

    private fun upload(field: PushConstantField, memoryStack: MemoryStack, sourceAddress: NativeAddress) {
        upload(field.name, field.type, memoryStack, sourceAddress)
    }

    private fun upload(name: String, type: ShaderValueType, memoryStack: MemoryStack, sourceAddress: NativeAddress) {
        val location = uniformLocation(name)
        when (type) {
            ShaderValueType.Int -> GL20.glUniform1i(location, memoryStack.loadInt(sourceAddress))
            ShaderValueType.Float -> GL20.glUniform1f(location, memoryStack.loadFloat(sourceAddress))
            ShaderValueType.Float2 -> GL20.glUniform2f(location, memoryStack.loadFloat(sourceAddress), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES))
            ShaderValueType.Float3 -> GL20.glUniform3f(location, memoryStack.loadFloat(sourceAddress), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES * 2))
            ShaderValueType.Float4 -> GL20.glUniform4f(location, memoryStack.loadFloat(sourceAddress), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES * 2), memoryStack.loadFloat(sourceAddress + Float.SIZE_BYTES * 3))
            ShaderValueType.Matrix4 -> binding.uniformMatrix4(location, false, sourceAddress)
        }
    }

    private fun uniformLocation(name: String): Int {
        check(programIdentifier >= 0) { "Render pipeline has already been closed" }
        return uniformLocations.getOrPut(name) { GL20.glGetUniformLocation(programIdentifier, name) }
    }

    private fun link(description: RenderPipelineDescription): Int {
        val vertexShader = description.vertexShader as? OpenGLShaderModule ?: error("OpenGL pipeline requires an OpenGL vertex shader")
        val fragmentShader = description.fragmentShader as? OpenGLShaderModule ?: error("OpenGL pipeline requires an OpenGL fragment shader")
        val identifier = GL20.glCreateProgram()

        try {
            GL20.glAttachShader(identifier, vertexShader.identifier())
            GL20.glAttachShader(identifier, fragmentShader.identifier())
            for ((_, _, attributes) in description.vertexBufferLayout.bindings) {
                for ((name, location) in attributes) GL20.glBindAttribLocation(identifier, location, name)
            }
            GL20.glLinkProgram(identifier)
            check(GL20.glGetProgrami(identifier, GL20.GL_LINK_STATUS) != GL11.GL_FALSE) { "Failed to link ${description.label}: ${GL20.glGetProgramInfoLog(identifier, MAXIMUM_LOG_LENGTH)}" }
            return identifier
        } catch (throwable: Throwable) {
            GL20.glDeleteProgram(identifier)
            throw throwable
        }
    }

    private fun applyBlendState(state: BlendState) {
        if (!state.isEnabled) {
            GL11.glDisable(GL11.GL_BLEND)
            return
        }

        GL11.glEnable(GL11.GL_BLEND)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)
        GL14.glBlendFuncSeparate(state.sourceColorFactor.openGLIdentifier, state.destinationColorFactor.openGLIdentifier, state.sourceAlphaFactor.openGLIdentifier, state.destinationAlphaFactor.openGLIdentifier)
    }

    private fun applyDepthState(state: DepthState) {
        if (state.isTestEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(state.isWriteEnabled)
        GL11.glDepthFunc(state.compareOperation.openGLIdentifier)
    }

    private fun applyCullMode(mode: CullMode) {
        when (mode) {
            CullMode.None -> GL11.glDisable(GL11.GL_CULL_FACE)
            CullMode.Back -> {
                GL11.glEnable(GL11.GL_CULL_FACE)
                GL11.glCullFace(GL11.GL_BACK)
            }
        }
    }

    private companion object {
        const val MAXIMUM_LOG_LENGTH = 32768
    }
}

private data class DescriptorLocation(val set: Int, val binding: Int)

private data class EmulatedUniformBufferState(
    val buffer: OpenGLBuffer,
    val revision: Long,
    val offsetBytes: Int,
    val sizeBytes: Int,
)

internal val PrimitiveTopology.openGLIdentifier: Int
    get() = when (this) {
        PrimitiveTopology.Lines -> GL11.GL_LINES
        PrimitiveTopology.TriangleList -> GL11.GL_TRIANGLES
        PrimitiveTopology.TriangleStrip -> GL11.GL_TRIANGLE_STRIP
    }

private val BlendFactor.openGLIdentifier: Int
    get() = when (this) {
        BlendFactor.Zero -> GL11.GL_ZERO
        BlendFactor.One -> GL11.GL_ONE
        BlendFactor.SourceAlpha -> GL11.GL_SRC_ALPHA
        BlendFactor.OneMinusSourceAlpha -> GL11.GL_ONE_MINUS_SRC_ALPHA
    }

private val CompareOperation.openGLIdentifier: Int
    get() = when (this) {
        CompareOperation.Always -> GL11.GL_ALWAYS
        CompareOperation.LessOrEqual -> GL11.GL_LEQUAL
    }
