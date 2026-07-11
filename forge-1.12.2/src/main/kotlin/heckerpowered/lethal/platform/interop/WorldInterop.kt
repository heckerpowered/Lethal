/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.lethal.platform.adapter.world.HostedWorldAccess
import net.minecraft.world.World

/**
 * Converts worlds across the bridge boundary.
 *
 * Native-to-bridge conversion can fall back to [HostedWorldAccess]; bridge-to-native conversion requires a known hosted or native world.
 */
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
