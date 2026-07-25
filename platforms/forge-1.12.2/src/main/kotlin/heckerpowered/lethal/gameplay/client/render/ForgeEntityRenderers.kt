/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.gameplay.client.render

import heckerpowered.lethal.gameplay.common.entity.ForgeStarJudgementEntity
import net.minecraftforge.fml.client.registry.RenderingRegistry

object ForgeEntityRenderers {
    init {
        RenderingRegistry.registerEntityRenderingHandler(ForgeStarJudgementEntity::class.java) { renderManager ->
            StarJudgementRenderer(renderManager)
        }
    }

    fun onInitialize() {
    }
}
