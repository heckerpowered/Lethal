/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class PrimitiveTopology {
    Lines,
    TriangleList,
    TriangleStrip,
}

enum class BlendFactor {
    Zero,
    One,
    SourceAlpha,
    OneMinusSourceAlpha,
}

enum class BlendOperation {
    Add,
}

data class BlendState(
    val isEnabled: Boolean,
    val sourceColorFactor: BlendFactor,
    val destinationColorFactor: BlendFactor,
    val colorOperation: BlendOperation,
    val sourceAlphaFactor: BlendFactor,
    val destinationAlphaFactor: BlendFactor,
    val alphaOperation: BlendOperation,
) {
    companion object {
        val Disabled = BlendState(false, BlendFactor.One, BlendFactor.Zero, BlendOperation.Add, BlendFactor.One, BlendFactor.Zero, BlendOperation.Add)
        val Additive = BlendState(true, BlendFactor.One, BlendFactor.One, BlendOperation.Add, BlendFactor.One, BlendFactor.One, BlendOperation.Add)
        val AdditiveColor = BlendState(true, BlendFactor.One, BlendFactor.One, BlendOperation.Add, BlendFactor.One, BlendFactor.Zero, BlendOperation.Add)
        val PremultipliedAlpha = BlendState(true, BlendFactor.One, BlendFactor.OneMinusSourceAlpha, BlendOperation.Add, BlendFactor.One, BlendFactor.OneMinusSourceAlpha, BlendOperation.Add)
        val SourceAlphaAdditive = BlendState(true, BlendFactor.SourceAlpha, BlendFactor.One, BlendOperation.Add, BlendFactor.One, BlendFactor.Zero, BlendOperation.Add)
    }
}

enum class CompareOperation {
    Always,
    LessOrEqual,
}

data class DepthState(
    val isTestEnabled: Boolean,
    val isWriteEnabled: Boolean,
    val compareOperation: CompareOperation,
) {
    companion object {
        val Disabled = DepthState(isTestEnabled = false, isWriteEnabled = false, compareOperation = CompareOperation.Always)
        val ReadOnly = DepthState(isTestEnabled = true, isWriteEnabled = false, compareOperation = CompareOperation.LessOrEqual)
    }
}

enum class CullMode {
    None,
    Back,
}
