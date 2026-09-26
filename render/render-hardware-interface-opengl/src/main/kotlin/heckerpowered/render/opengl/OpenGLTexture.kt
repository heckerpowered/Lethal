/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.opengl.function.*
import heckerpowered.render.pipeline.multisample.SampleCount
import heckerpowered.render.resource.texture.*
import heckerpowered.render.terminateOnFailure

/** One allocation; views and attachments keep a reference to it rather than owning another image. */
internal class OpenGLTexture private constructor(
    val device: OpenGLGraphicsDevice,
    private val description: TextureDescription,
    private var textureName: TextureName,
) : GpuTexture {
    val name: TextureName
        get() {
            checkOpen()
            return textureName
        }

    override val dimension get() = description.dimension
    override val storage get() = description.storage
    override val usage get() = description.usage
    override val sampleCount get() = description.sampleCount
    override val width get() = description.width
    override val height get() = description.height
    override val depth get() = description.depth
    override val format get() = description.format
    override val mipLevelCount get() = description.mipLevelCount
    override val arrayLayerCount get() = description.arrayLayerCount
    override val cubeCompatible get() = description.cubeCompatible

    fun checkOpen() {
        device.checkAccess()
        check(textureName != TextureName.None) { "Texture '${description.label}' is closed" }
    }

    override fun close() = terminateOnFailure {
        if (textureName == TextureName.None) return@terminateOnFailure
        context(device, device.functions) {
            device.checkAccess()
            val isActiveAttachment = device.isActiveAttachment(this@OpenGLTexture)
            check(!isActiveAttachment) { "Cannot destroy a texture while its render pass is active" }
            checkError("before texture destruction")
            deleteTexture(textureName)
            checkError("texture destruction")
            textureName = TextureName.None
        }
    }

    companion object {
        context(_: OpenGLTextureFunctions)
        fun validateDescription(description: TextureDescription) {
            validateShape(description)
            validateStorage(description)
            validateFormat(description)
            validateUsage(description)
            validateDimensions(description)
        }

        private fun validateShape(description: TextureDescription) {
            val isTwoDimensional = description.dimension == TextureDimension.TwoDimensional && description.depth == 1
            val selectsSingleImage = description.arrayLayerCount == 1 && description.mipLevelCount == 1
            val isSupportedShape = isTwoDimensional && selectsSingleImage && !description.cubeCompatible
            if (!isSupportedShape) {
                throw UnsupportedOperationException("Only one two-dimensional mip and layer are implemented")
            }
        }

        private fun validateStorage(description: TextureDescription) {
            val isSingleSampled = description.sampleCount == SampleCount.One
            val hasBackingStorage = description.storage == TextureStorage.Backed
            if (!isSingleSampled || !hasBackingStorage) {
                throw UnsupportedOperationException("Only single-sampled backed textures are implemented")
            }
        }

        private fun validateFormat(description: TextureDescription) {
            if (description.format != TextureFormat.Rgba8UnsignedNormalized) {
                throw UnsupportedOperationException("Only Rgba8UnsignedNormalized storage is implemented")
            }
        }

        private fun validateUsage(description: TextureDescription) {
            val hasUnimplementedUsage = description.usage.any { it != TextureUsage.ColorAttachment }
            if (hasUnimplementedUsage) {
                throw UnsupportedOperationException("Only ColorAttachment usage is implemented")
            }
        }

        context(_: OpenGLTextureFunctions)
        private fun validateDimensions(description: TextureDescription) {
            val dimensionsArePowersOfTwo = isPowerOfTwo(description.width) && isPowerOfTwo(description.height)
            val dimensionsAreSupported = supportsNonPowerOfTwoTextures || dimensionsArePowersOfTwo
            if (!dimensionsAreSupported) {
                throw UnsupportedOperationException("The current context requires power-of-two texture dimensions")
            }
        }

        private fun isPowerOfTwo(value: Int): Boolean = value > 0 && value and (value - 1) == 0

        context(device: OpenGLGraphicsDevice)
        fun create(description: TextureDescription): OpenGLTexture = context(device.functions) {
            device.checkAccess()
            validateDescription(description)
            checkError("before texture creation")
            validateTextureSize(description)

            // The name remains ours until the Kotlin wrapper exists. This finally also covers
            // Error exits without intercepting the failure or assuming wrapper allocation cannot fail.
            val name = allocateTextureName(description)
            var transferred = false
            try {
                withTextureBinding(name) {
                    allocateStorage(description)
                    verifyStorage(description)
                }
                val texture = OpenGLTexture(device, description, name)
                transferred = true
                texture
            } finally {
                if (!transferred) releaseTexture(name)
            }
        }

        context(_: OpenGLFunctions)
        private fun validateTextureSize(description: TextureDescription) {
            val maximum = getInteger(GL_MAX_TEXTURE_SIZE)
            checkError("texture dimension limit")
            val exceedsMaximumSize = description.width > maximum || description.height > maximum
            if (exceedsMaximumSize) {
                throw UnsupportedOperationException("Texture dimensions exceed GL_MAX_TEXTURE_SIZE=$maximum")
            }
        }

        context(_: OpenGLFunctions)
        private fun allocateTextureName(description: TextureDescription): TextureName {
            val name = createTexture()
            if (name == TextureName.None) {
                checkError("texture name allocation")
                throw TextureCreationException("OpenGL returned no name for texture '${description.label}'")
            }
            return name
        }

        context(_: OpenGLFunctions)
        private fun allocateStorage(description: TextureDescription) {
            textureImage2D(0, GL_RGBA8, description.width, description.height, GL_RGBA, GL_UNSIGNED_BYTE, null)
            textureParameter(GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            textureParameter(GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            checkError("texture '${description.label}' allocation")
        }

        context(_: OpenGLFunctions)
        private fun verifyStorage(description: TextureDescription) {
            val width = getTextureLevelParameter(0, GL_TEXTURE_WIDTH)
            val height = getTextureLevelParameter(0, GL_TEXTURE_HEIGHT)
            val format = getTextureLevelParameter(0, GL_TEXTURE_INTERNAL_FORMAT)
            checkError("texture allocation verification")
            val dimensionsMatch = width == description.width && height == description.height
            val formatMatches = format == GL_RGBA8
            if (!dimensionsMatch || !formatMatches) {
                throw TextureCreationException("OpenGL did not establish texture '${description.label}' as requested")
            }
        }
    }
}

/**
 * Binds one texture for storage allocation and restores the surrounding bindings on every exit.
 * Null pixel data means no upload only when the unpack buffer is unbound; otherwise it is offset zero.
 * This scope borrows the name. It never deletes it or decides whether creation succeeded.
 */
context(device: OpenGLGraphicsDevice, _: OpenGLFunctions)
private inline fun <R> withTextureBinding(texture: TextureName, operation: () -> R): R {
    val previousTexture = getBoundTexture2D()
    val previousUnpack = if (supportsPixelBuffers) getBoundPixelUnpackBuffer() else null
    checkError("texture binding snapshot")
    try {
        bindTexture2D(texture)
        if (previousUnpack != null) bindPixelUnpackBuffer(BufferName.None)
        return operation()
    } finally {
        terminateOnFailure {
            device.checkAccess()
            bindTexture2D(previousTexture)
            if (previousUnpack != null) bindPixelUnpackBuffer(previousUnpack)
            checkError("texture binding restoration")
        }
    }
}

context(device: OpenGLGraphicsDevice, _: OpenGLFunctions)
private fun releaseTexture(texture: TextureName) = terminateOnFailure {
    device.checkAccess()
    deleteTexture(texture)
    checkError("failed texture creation cleanup")
}
