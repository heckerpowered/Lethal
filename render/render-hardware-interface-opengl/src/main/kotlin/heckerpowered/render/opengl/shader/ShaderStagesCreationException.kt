/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.opengl.shader

import heckerpowered.render.shader.ShaderStagesDescription

class ShaderStagesCreationException(
    val description: ShaderStagesDescription,
    val diagnostics: String,
) : RuntimeException(
    buildString {
        append("Failed to link shader stages \"")
        append(description.label)
        append('"')
        if (diagnostics.isNotBlank()) {
            append(":\n")
            append(diagnostics)
        }
    },
)
