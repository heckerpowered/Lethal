/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

@JvmInline
value class GraphicsBackendId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Graphics backend ID must not be blank"
        }
    }

    override fun toString(): String = value

    companion object {
        val OpenGL = GraphicsBackendId("opengl")
        val Vulkan = GraphicsBackendId("vulkan")
    }
}