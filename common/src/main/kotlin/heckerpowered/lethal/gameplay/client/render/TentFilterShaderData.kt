/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.render.Float2
import heckerpowered.render.PushConstantBlock
import heckerpowered.render.ShaderStage

@PushConstantBlock(ShaderStage.Fragment)
internal interface TentFilterConstants {
    val texelSize: Float2
}
