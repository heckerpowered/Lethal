/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.lwjgl2

import heckerpowered.render.memory.NativeAddress
import heckerpowered.render.opengl.*
import heckerpowered.render.opengl.function.*
import org.lwjgl.PointerBuffer
import org.lwjgl.opengl.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class Lwjgl2OpenGLFunctions(
    private val shaderEntryPoints: Lwjgl2ShaderEntryPoints,
    private val bufferEntryPoints: Lwjgl2BufferEntryPoints,
    private val multitextureEntryPoints: Lwjgl2MultitextureEntryPoints,
    private val vertexInputEntryPoints: Lwjgl2VertexInputEntryPoints,
    private val blendFunctionEntryPoints: Lwjgl2BlendFunctionEntryPoints,
    private val blendEquationEntryPoints: Lwjgl2BlendEquationEntryPoints,
    override val framebuffers: OpenGLFramebufferFunctions?,
    override val vertexArrays: OpenGLVertexArrayFunctions?,
    override val uniformBuffers: OpenGLUniformBufferFunctions?,
    override val samplers: OpenGLSamplerFunctions?,
    override val shaderColorClamping: OpenGLShaderColorClampingFunctions?,
) : OpenGLFunctions {
    private val context = GLContext.getCapabilities()
    private val thread = Thread.currentThread()
    private val nativeBufferSubData = when (bufferEntryPoints) {
        Lwjgl2BufferEntryPoints.Core -> Lwjgl2BufferSubData.create(context, GL15::class.java, "glBufferSubData")
        Lwjgl2BufferEntryPoints.ARB -> Lwjgl2BufferSubData.create(context, ARBBufferObject::class.java, "glBufferSubDataARB")
    }
    override val maximumBufferSizeBytes: Long = if (PointerBuffer.getPointerSize() == 8) Long.MAX_VALUE else Int.MAX_VALUE.toLong()
    private val viewportArrays = context.OpenGL41 || context.GL_ARB_viewport_array

    init {
        check(!viewportArrays || context.OpenGL30 || context.GL_EXT_draw_buffers2) { "Viewport-array scissor state requires core or EXT indexed state entry points" }
    }

    override val spirVShaders: OpenGLSpirVShaderFunctions? = null

    override val supportsRasterizerDiscard: Boolean = context.OpenGL30 || context.GL_EXT_transform_feedback || context.GL_NV_transform_feedback
    override val supportsNonPowerOfTwoTextures: Boolean = context.OpenGL20 || context.GL_ARB_texture_non_power_of_two
    override val supportsPixelBuffers: Boolean = context.OpenGL21 || context.GL_ARB_pixel_buffer_object || context.GL_EXT_pixel_buffer_object

    override fun checkCurrentContext() {
        check(Thread.currentThread() === thread) { "OpenGL access from a different thread" }
        check(GLContext.getCapabilities() === context) { "The original OpenGL context is not current" }
    }

    override fun getError(): Int = GL11.glGetError()
    override fun flush() = GL11.glFlush()
    override fun getFloats(parameter: Int, destination: FloatBuffer) = GL11.glGetFloat(parameter, destination)

    override fun getBoundPixelUnpackBuffer(): BufferName {
        check(supportsPixelBuffers) { "Pixel buffer objects are unavailable" }
        return BufferName(GL11.glGetInteger(ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_BINDING_ARB))
    }

    override fun bindPixelUnpackBuffer(buffer: BufferName) {
        check(supportsPixelBuffers) { "Pixel buffer objects are unavailable" }
        bufferEntryPoints.bindBuffer(ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB, buffer.value)
    }

    override fun getTextureLevelParameter(level: Int, parameter: Int): Int =
        GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, parameter)

    override fun scissor(index: Int, x: Int, y: Int, width: Int, height: Int) {
        when {
            context.OpenGL41 -> GL41.glScissorIndexed(index, x, y, width, height)
            context.GL_ARB_viewport_array -> ARBViewportArray.glScissorIndexed(index, x, y, width, height)
            else -> {
                require(index == 0) { "Only scissor zero is available" }
                GL11.glScissor(x, y, width, height)
            }
        }
    }

    override fun getScissorBox(index: Int, destination: IntBuffer) {
        if (viewportArrays) {
            if (context.OpenGL30) GL30.glGetInteger(GL11.GL_SCISSOR_BOX, index, destination)
            else EXTDrawBuffers2.glGetIntegerIndexedEXT(GL11.GL_SCISSOR_BOX, index, destination)
        } else {
            require(index == 0) { "Only scissor zero is available" }
            getIntegers(GL11.GL_SCISSOR_BOX, destination)
        }
    }

    override fun isScissorEnabled(index: Int): Boolean {
        if (viewportArrays) {
            return if (context.OpenGL30) GL30.glIsEnabledi(GL11.GL_SCISSOR_TEST, index)
            else EXTDrawBuffers2.glIsEnabledIndexedEXT(GL11.GL_SCISSOR_TEST, index)
        }
        require(index == 0) { "Only scissor zero is available" }
        return GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
    }

    override fun setScissorEnabled(index: Int, enabled: Boolean) {
        if (viewportArrays) {
            if (context.OpenGL30) {
                if (enabled) GL30.glEnablei(GL11.GL_SCISSOR_TEST, index) else GL30.glDisablei(GL11.GL_SCISSOR_TEST, index)
            } else {
                if (enabled) EXTDrawBuffers2.glEnableIndexedEXT(GL11.GL_SCISSOR_TEST, index)
                else EXTDrawBuffers2.glDisableIndexedEXT(GL11.GL_SCISSOR_TEST, index)
            }
        } else {
            require(index == 0) { "Only scissor zero is available" }
            if (enabled) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
    }

    override fun colorMask(index: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        when {
            context.OpenGL30 -> GL30.glColorMaski(index, red, green, blue, alpha)
            context.GL_EXT_draw_buffers2 -> EXTDrawBuffers2.glColorMaskIndexedEXT(index, red, green, blue, alpha)
            else -> {
                require(index == 0) { "Only the shared color mask is available" }
                GL11.glColorMask(red, green, blue, alpha)
            }
        }
    }

    override fun createShader(type: ShaderType): ShaderName {
        val name = when (shaderEntryPoints) {
            Lwjgl2ShaderEntryPoints.Core -> GL20.glCreateShader(type.toOpenGL())
            Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glCreateShaderObjectARB(type.toOpenGL())
        }
        return ShaderName(name)
    }

    override fun shaderSource(shader: ShaderName, source: CharSequence) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glShaderSource(shader.value, source)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glShaderSourceARB(shader.value, source)
    }

    override fun compileShader(shader: ShaderName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glCompileShader(shader.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glCompileShaderARB(shader.value)
    }

    override fun getShaderCompileStatus(shader: ShaderName): Boolean = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glGetShaderi(shader.value, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glGetObjectParameteriARB(shader.value, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB) == GL11.GL_TRUE
    }

    override fun getShaderInfoLog(shader: ShaderName): String = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> {
            val length = GL20.glGetShaderi(shader.value, GL20.GL_INFO_LOG_LENGTH)
            if (length <= 1) "" else GL20.glGetShaderInfoLog(shader.value, length)
        }

        Lwjgl2ShaderEntryPoints.ARB -> {
            val length = ARBShaderObjects.glGetObjectParameteriARB(shader.value, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB)
            if (length <= 1) "" else ARBShaderObjects.glGetInfoLogARB(shader.value, length)
        }
    }

    override fun deleteShader(shader: ShaderName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glDeleteShader(shader.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glDeleteObjectARB(shader.value)
    }

    override fun createProgram(): ProgramName {
        val name = when (shaderEntryPoints) {
            Lwjgl2ShaderEntryPoints.Core -> GL20.glCreateProgram()
            Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glCreateProgramObjectARB()
        }
        return ProgramName(name)
    }

    override fun attachShader(program: ProgramName, shader: ShaderName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glAttachShader(program.value, shader.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glAttachObjectARB(program.value, shader.value)
    }

    override fun bindVertexAttributeLocation(program: ProgramName, index: VertexAttributeIndex, name: CharSequence) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glBindAttribLocation(program.value, index.value, name)
        Lwjgl2ShaderEntryPoints.ARB -> ARBVertexShader.glBindAttribLocationARB(program.value, index.value, name)
    }

    override fun linkProgram(program: ProgramName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glLinkProgram(program.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glLinkProgramARB(program.value)
    }

    override fun getProgramLinkStatus(program: ProgramName): Boolean = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glGetProgrami(program.value, GL20.GL_LINK_STATUS) == GL11.GL_TRUE
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glGetObjectParameteriARB(program.value, ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB) == GL11.GL_TRUE
    }

    override fun getProgramInfoLog(program: ProgramName): String = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> {
            val length = GL20.glGetProgrami(program.value, GL20.GL_INFO_LOG_LENGTH)
            if (length <= 1) "" else GL20.glGetProgramInfoLog(program.value, length)
        }

        Lwjgl2ShaderEntryPoints.ARB -> {
            val length = ARBShaderObjects.glGetObjectParameteriARB(program.value, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB)
            if (length <= 1) "" else ARBShaderObjects.glGetInfoLogARB(program.value, length)
        }
    }

    override fun useProgram(program: ProgramName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUseProgram(program.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUseProgramObjectARB(program.value)
    }

    override fun deleteProgram(program: ProgramName) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glDeleteProgram(program.value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glDeleteObjectARB(program.value)
    }

    override fun getUniformLocation(program: ProgramName, name: CharSequence): UniformLocation {
        val location = when (shaderEntryPoints) {
            Lwjgl2ShaderEntryPoints.Core -> GL20.glGetUniformLocation(program.value, name)
            Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glGetUniformLocationARB(program.value, name)
        }
        return UniformLocation(location)
    }

    override fun uniformInt(location: UniformLocation, value: Int) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniform1i(location.value, value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniform1iARB(location.value, value)
    }

    override fun uniformFloat(location: UniformLocation, value: Float) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniform1f(location.value, value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniform1fARB(location.value, value)
    }

    override fun uniformFloat2(location: UniformLocation, x: Float, y: Float) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniform2f(location.value, x, y)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniform2fARB(location.value, x, y)
    }

    override fun uniformFloat3(location: UniformLocation, x: Float, y: Float, z: Float) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniform3f(location.value, x, y, z)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniform3fARB(location.value, x, y, z)
    }

    override fun uniformFloat4(location: UniformLocation, x: Float, y: Float, z: Float, w: Float) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniform4f(location.value, x, y, z, w)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniform4fARB(location.value, x, y, z, w)
    }

    override fun uniformMatrix4(location: UniformLocation, transpose: Boolean, value: FloatBuffer) = when (shaderEntryPoints) {
        Lwjgl2ShaderEntryPoints.Core -> GL20.glUniformMatrix4(location.value, transpose, value)
        Lwjgl2ShaderEntryPoints.ARB -> ARBShaderObjects.glUniformMatrix4ARB(location.value, transpose, value)
    }

    override fun createBuffer(): BufferName = BufferName(bufferEntryPoints.generateBuffer())

    override fun getBoundBuffer(target: BufferTarget): BufferName = BufferName(GL11.glGetInteger(target.toOpenGLBinding()))

    override fun bindBuffer(target: BufferTarget, buffer: BufferName) {
        bufferEntryPoints.bindBuffer(target.toOpenGL(), buffer.value)
    }

    override fun bufferData(target: BufferTarget, sizeBytes: Long, usage: BufferUsage) {
        bufferEntryPoints.bufferData(target.toOpenGL(), sizeBytes, usage.toOpenGL())
    }

    override fun bufferData(target: BufferTarget, data: ByteBuffer, usage: BufferUsage) {
        bufferEntryPoints.bufferData(target.toOpenGL(), data, usage.toOpenGL())
    }

    override fun bufferSubData(target: BufferTarget, offsetBytes: Long, data: ByteBuffer) {
        bufferEntryPoints.bufferSubData(target.toOpenGL(), offsetBytes, data)
    }

    override fun bufferSubData(target: BufferTarget, offsetBytes: Long, sizeBytes: Long, sourceAddress: NativeAddress) {
        validateNativeUpload(offsetBytes, sizeBytes, sourceAddress, maximumBufferSizeBytes)
        if (sizeBytes == 0L) return
        checkCurrentContext()
        nativeBufferSubData.upload(target.toOpenGL(), offsetBytes, sizeBytes, sourceAddress.rawValue)
    }

    override fun deleteBuffer(buffer: BufferName) {
        bufferEntryPoints.deleteBuffer(buffer.value)
    }

    override fun createTexture(): TextureName = TextureName(GL11.glGenTextures())

    override fun getActiveTextureUnit(): TextureUnit {
        return TextureUnit(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0)
    }

    override fun activeTexture(unit: TextureUnit) = when (multitextureEntryPoints) {
        Lwjgl2MultitextureEntryPoints.Core -> GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit.value)
        Lwjgl2MultitextureEntryPoints.ARB -> ARBMultitexture.glActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit.value)
    }

    override fun getBoundTexture2D(): TextureName = TextureName(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D))

    override fun bindTexture2D(texture: TextureName) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.value)
    }

    override fun textureImage2D(level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer?) {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, internalFormat, width, height, 0, format, type, pixels)
    }

    override fun textureParameter(parameter: Int, value: Int) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, parameter, value)
    }

    override fun getTextureParameter(parameter: Int): Int = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, parameter)

    override fun deleteTexture(texture: TextureName) {
        GL11.glDeleteTextures(texture.value)
    }

    override fun enableVertexAttribute(index: VertexAttributeIndex) = when (vertexInputEntryPoints) {
        Lwjgl2VertexInputEntryPoints.Core -> GL20.glEnableVertexAttribArray(index.value)
        Lwjgl2VertexInputEntryPoints.ARB -> ARBVertexShader.glEnableVertexAttribArrayARB(index.value)
    }

    override fun disableVertexAttribute(index: VertexAttributeIndex) = when (vertexInputEntryPoints) {
        Lwjgl2VertexInputEntryPoints.Core -> GL20.glDisableVertexAttribArray(index.value)
        Lwjgl2VertexInputEntryPoints.ARB -> ARBVertexShader.glDisableVertexAttribArrayARB(index.value)
    }

    override fun vertexAttributePointer(index: VertexAttributeIndex, componentCount: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Long) =
        when (vertexInputEntryPoints) {
            Lwjgl2VertexInputEntryPoints.Core -> GL20.glVertexAttribPointer(index.value, componentCount, type, normalized, strideBytes, offsetBytes)
            Lwjgl2VertexInputEntryPoints.ARB -> ARBVertexShader.glVertexAttribPointerARB(index.value, componentCount, type, normalized, strideBytes, offsetBytes)
        }

    override fun drawArrays(mode: PrimitiveMode, firstVertex: Int, vertexCount: Int) {
        GL11.glDrawArrays(mode.toOpenGL(), firstVertex, vertexCount)
    }

    override fun getInteger(parameter: Int): Int = GL11.glGetInteger(parameter)

    override fun getIntegers(parameter: Int, destination: IntBuffer) {
        GL11.glGetInteger(parameter, destination)
    }

    override fun isEnabled(capability: Int): Boolean = GL11.glIsEnabled(capability)

    override fun enable(capability: Int) = GL11.glEnable(capability)

    override fun disable(capability: Int) = GL11.glDisable(capability)

    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GL11.glViewport(x, y, width, height)

    override fun blendFunctionSeparate(sourceRgbFactor: Int, destinationRgbFactor: Int, sourceAlphaFactor: Int, destinationAlphaFactor: Int) =
        when (blendFunctionEntryPoints) {
            Lwjgl2BlendFunctionEntryPoints.Core -> GL14.glBlendFuncSeparate(sourceRgbFactor, destinationRgbFactor, sourceAlphaFactor, destinationAlphaFactor)
            Lwjgl2BlendFunctionEntryPoints.EXT -> EXTBlendFuncSeparate.glBlendFuncSeparateEXT(sourceRgbFactor, destinationRgbFactor, sourceAlphaFactor, destinationAlphaFactor)
        }

    override fun blendEquation(equation: BlendEquation) = when (blendEquationEntryPoints) {
        Lwjgl2BlendEquationEntryPoints.Core -> GL14.glBlendEquation(equation.toOpenGL())
        Lwjgl2BlendEquationEntryPoints.EXT -> EXTBlendMinmax.glBlendEquationEXT(equation.toOpenGL())
    }

    override fun depthFunction(compareFunction: Int) = GL11.glDepthFunc(compareFunction)

    override fun depthMask(enabled: Boolean) = GL11.glDepthMask(enabled)

    override fun cullFace(mode: Int) = GL11.glCullFace(mode)

    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) = GL11.glColorMask(red, green, blue, alpha)

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) = GL11.glClearColor(red, green, blue, alpha)

    override fun clearDepth(depth: Double) = GL11.glClearDepth(depth)

    override fun clear(mask: Int) = GL11.glClear(mask)
}

internal fun Lwjgl2BufferEntryPoints.generateBuffer(): Int = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glGenBuffers()
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glGenBuffersARB()
}

internal fun Lwjgl2BufferEntryPoints.bindBuffer(target: Int, buffer: Int) = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glBindBuffer(target, buffer)
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glBindBufferARB(target, buffer)
}

internal fun Lwjgl2BufferEntryPoints.bufferData(target: Int, sizeBytes: Long, usage: Int) = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glBufferData(target, sizeBytes, usage)
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glBufferDataARB(target, sizeBytes, usage)
}

internal fun Lwjgl2BufferEntryPoints.bufferData(target: Int, data: ByteBuffer, usage: Int) = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glBufferData(target, data, usage)
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glBufferDataARB(target, data, usage)
}

internal fun Lwjgl2BufferEntryPoints.bufferSubData(target: Int, offsetBytes: Long, data: ByteBuffer) = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glBufferSubData(target, offsetBytes, data)
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glBufferSubDataARB(target, offsetBytes, data)
}

internal fun Lwjgl2BufferEntryPoints.deleteBuffer(buffer: Int) = when (this) {
    Lwjgl2BufferEntryPoints.Core -> GL15.glDeleteBuffers(buffer)
    Lwjgl2BufferEntryPoints.ARB -> ARBBufferObject.glDeleteBuffersARB(buffer)
}

private fun validateNativeUpload(offsetBytes: Long, sizeBytes: Long, sourceAddress: NativeAddress, nativeSizeLimit: Long) {
    require(offsetBytes in 0..nativeSizeLimit) { "Buffer upload offset is outside the native GLintptr range" }
    require(sizeBytes in 0..nativeSizeLimit - offsetBytes) { "Buffer upload range is outside the native GLsizeiptr range" }
    if (sizeBytes == 0L) return

    require(sourceAddress.rawValue != 0L) { "A nonempty buffer upload requires a nonzero source address" }
    val firstByte = sourceAddress.rawValue.toULong()
    val lastByte = firstByte + (sizeBytes - 1).toULong()
    val wrapsAddressSpace = lastByte < firstByte
    val exceeds32BitAddressSpace = nativeSizeLimit == Int.MAX_VALUE.toLong() && lastByte > UInt.MAX_VALUE.toULong()
    require(!wrapsAddressSpace && !exceeds32BitAddressSpace) { "Buffer upload source range cannot be represented by a native pointer" }
}
