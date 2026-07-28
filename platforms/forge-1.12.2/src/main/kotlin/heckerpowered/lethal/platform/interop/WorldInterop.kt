/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.adapter.world.WorldAccess
import heckerpowered.bridge.requireAccess
import heckerpowered.bridge.requireHost
import net.minecraft.world.World

object WorldInterop {
    @JvmStatic
    fun asHost(world: WorldAccess): World = world.asHost()

    @JvmStatic
    fun asView(world: World): WorldAccess = world.asView()
}

fun World.asView(): WorldAccess = requireAccess(this)

fun WorldAccess.asHost(): World = requireHost(this)
