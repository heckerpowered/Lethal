/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

import heckerpowered.render.opengl.BufferName
import heckerpowered.render.opengl.TextureName
import heckerpowered.render.opengl.TextureUnit
import java.nio.ByteBuffer

/**
 * Allocates two-dimensional textures and manages their image-unit bindings and
 * sampling parameters.
 *
 * The newest mandatory core operation in this group is active-texture selection;
 * adapters may normalize `ARB_multitexture` when core OpenGL 1.3 is absent.
 */
interface OpenGLTextureFunctions {
    val supportsNonPowerOfTwoTextures: Boolean
    val supportsPixelBuffers: Boolean

    /** These two operations require [supportsPixelBuffers]; absence is not a zero binding. */
    fun getBoundPixelUnpackBuffer(): BufferName
    fun bindPixelUnpackBuffer(buffer: BufferName)

    fun getTextureLevelParameter(level: Int, parameter: Int): Int
    fun createTexture(): TextureName
    fun getActiveTextureUnit(): TextureUnit
    fun activeTexture(unit: TextureUnit)
    fun getBoundTexture2D(): TextureName
    fun bindTexture2D(texture: TextureName)

    fun textureImage2D(level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer?)

    fun textureParameter(parameter: Int, value: Int)
    fun getTextureParameter(parameter: Int): Int
    fun deleteTexture(texture: TextureName)
}

context(function: OpenGLTextureFunctions)
fun createTexture(): TextureName = function.createTexture()

context(function: OpenGLTextureFunctions)
fun getActiveTextureUnit(): TextureUnit = function.getActiveTextureUnit()

context(function: OpenGLTextureFunctions)
fun activeTexture(unit: TextureUnit) = function.activeTexture(unit)

context(function: OpenGLTextureFunctions)
fun getBoundTexture2D(): TextureName = function.getBoundTexture2D()

context(function: OpenGLTextureFunctions)
fun bindTexture2D(texture: TextureName) = function.bindTexture2D(texture)

context(function: OpenGLTextureFunctions)
fun textureImage2D(
    level: Int,
    internalFormat: Int,
    width: Int,
    height: Int,
    format: Int,
    type: Int,
    pixels: ByteBuffer?,
) = function.textureImage2D(level, internalFormat, width, height, format, type, pixels)

context(function: OpenGLTextureFunctions)
fun textureParameter(parameter: Int, value: Int) = function.textureParameter(parameter, value)

context(function: OpenGLTextureFunctions)
fun getTextureParameter(parameter: Int): Int = function.getTextureParameter(parameter)

context(function: OpenGLTextureFunctions)
fun deleteTexture(texture: TextureName) = function.deleteTexture(texture)

context(function: OpenGLTextureFunctions)
val supportsNonPowerOfTwoTextures: Boolean
    get() = function.supportsNonPowerOfTwoTextures

context(function: OpenGLTextureFunctions)
val supportsPixelBuffers: Boolean
    get() = function.supportsPixelBuffers

context(function: OpenGLTextureFunctions)
fun getBoundPixelUnpackBuffer(): BufferName = function.getBoundPixelUnpackBuffer()

context(function: OpenGLTextureFunctions)
fun bindPixelUnpackBuffer(buffer: BufferName) = function.bindPixelUnpackBuffer(buffer)

context(function: OpenGLTextureFunctions)
fun getTextureLevelParameter(level: Int, parameter: Int): Int = function.getTextureLevelParameter(level, parameter)
