/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.adapter.block

import heckerpowered.bridge.adapter.block.BlockAccess
import heckerpowered.bridge.resources.Identifier
import heckerpowered.lethal.platform.interop.identifier
import net.minecraft.block.Block

class HostedBlockAccess(val block: Block) : BlockAccess {
    override val identifier: Identifier
        get() = requireNotNull(block.registryName) { "Block is not registered" }.identifier()
}
