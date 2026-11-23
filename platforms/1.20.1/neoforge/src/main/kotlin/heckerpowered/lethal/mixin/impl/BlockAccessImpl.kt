/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.world.level.block.Block
import net.minecraftforge.registries.ForgeRegistries

/**
 * Looks up the native block in Forge’s registry and exposes its registered identifier. The
 * identifier describes the block type rather than a block state at a world position.
 */
object BlockAccessImpl {
    @JvmStatic
    fun identifier(block: Block): Identifier {
        return checkNotNull(ForgeRegistries.BLOCKS.getKey(block)).asView()
    }
}
