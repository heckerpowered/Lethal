/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.pass

import heckerpowered.render.color.Color

data class RenderPassDescription(
    val label: String,
    val colorAttachments: List<RenderPassAttachment<Color>> = emptyList(),
    val depthAttachment: RenderPassAttachment<Float>? = null,
    val stencilAttachment: RenderPassAttachment<UByte>? = null,
)