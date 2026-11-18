/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */
package heckerpowered.render.shader

import heckerpowered.render.shader.primitive.PrimitiveShader

/**
 * Drawing programs established lazily and closed by the device. Callers use their stages without
 * closing them. Access requires that device's graphics context and thread. Stages contain
 * no host matrices, textures, attachments, fog or lighting. Unsupported programs fail on access.
 */
interface ShaderLibrary {
    operator fun get(shader: PrimitiveShader): ShaderStages

    val position: ShaderStages get() = get(PrimitiveShader.Position)
    val positionColor: ShaderStages get() = get(PrimitiveShader.PositionColor)
    val positionTexture: ShaderStages get() = get(PrimitiveShader.PositionTexture)
    val positionTextureColor: ShaderStages get() = get(PrimitiveShader.PositionTextureColor)
    val blitScreen: ShaderStages get() = get(PrimitiveShader.BlitScreen)
    val fillScreen: ShaderStages get() = get(PrimitiveShader.FillScreen)
}
