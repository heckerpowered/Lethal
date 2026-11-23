/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.asView
import net.minecraft.block.Block

/**
 * Looks up a native block in the host registry and exposes its namespaced identifier to bridge
 * code. The lookup describes the registered block type rather than a particular block state.
 */
object BlockAccessImpl {
    @JvmStatic
    fun identifier(block: Block): Identifier {
        val registryName = block.registryName ?: throw IllegalStateException("Block is not registered")

        return registryName.asView()
    }
}
