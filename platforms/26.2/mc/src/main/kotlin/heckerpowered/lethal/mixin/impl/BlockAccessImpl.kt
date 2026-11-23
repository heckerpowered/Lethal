/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.mixin.impl

import heckerpowered.bridge.resources.Identifier
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block

/**
 * Exposes a native block’s registry identifier as a bridge identifier. the lookup concerns the
 * block type, not a state at a particular position.
 */
object BlockAccessImpl {
    @JvmStatic
    fun identifier(block: Block): Identifier {
        val identifier = BuiltInRegistries.BLOCK.getKey(block)
        return Identifier.create(identifier.namespace, identifier.path)
    }
}
