/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.lethal.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.platform.adapter.world.HostedWorldAccess
import net.minecraft.world.World

object WorldInterop {
    @JvmStatic
    fun world(world: WorldAccess): World {
        if (world is HostedWorldAccess) return world.world

        @Suppress("CAST_NEVER_SUCCEEDS")
        return world as? World ?: error("Unsupported WorldAccess implementation: ${world.javaClass.name}")
    }

    fun world(world: World): WorldAccess {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return world as? WorldAccess ?: HostedWorldAccess(world)
    }
}

fun World.world(): WorldAccess {
    return WorldInterop.world(this)
}

fun WorldAccess.world(): World {
    return WorldInterop.world(this)
}
