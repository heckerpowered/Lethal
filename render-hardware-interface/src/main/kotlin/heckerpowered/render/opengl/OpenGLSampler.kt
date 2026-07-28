/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl

import heckerpowered.render.GpuSampler
import heckerpowered.render.SamplerAddressMode
import heckerpowered.render.SamplerDescription
import heckerpowered.render.TextureFilter
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12

internal class OpenGLSampler(val description: SamplerDescription) : GpuSampler {
    override fun close() = Unit
}

internal val TextureFilter.openGLIdentifier: Int
    get() = when (this) {
        TextureFilter.Nearest -> GL11.GL_NEAREST
        TextureFilter.Linear -> GL11.GL_LINEAR
    }

internal val SamplerAddressMode.openGLIdentifier: Int
    get() = when (this) {
        SamplerAddressMode.Repeat -> GL11.GL_REPEAT
        SamplerAddressMode.ClampToEdge -> GL12.GL_CLAMP_TO_EDGE
    }
