/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

enum class TextureFilter {
    Nearest,
    Linear,
}

enum class SamplerAddressMode {
    Repeat,
    ClampToEdge,
}

data class SamplerDescription(
    val minificationFilter: TextureFilter,
    val magnificationFilter: TextureFilter,
    val horizontalAddressMode: SamplerAddressMode,
    val verticalAddressMode: SamplerAddressMode,
)
