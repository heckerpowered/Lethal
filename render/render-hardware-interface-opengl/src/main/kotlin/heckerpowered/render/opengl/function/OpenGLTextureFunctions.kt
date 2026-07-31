/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.function

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