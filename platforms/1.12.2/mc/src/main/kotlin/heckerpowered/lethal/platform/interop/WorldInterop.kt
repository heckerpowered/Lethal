/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.interop

import heckerpowered.bridge.MixinInterop
import heckerpowered.bridge.adapter.world.WorldAccess
import net.minecraft.world.World

/**
 * Exposes the legacy host world as a bridge view and unwraps that view when a native world is
 * required.
 */
object WorldInterop {
    @JvmStatic
    fun asHost(world: WorldAccess): World = world.asHost()

    @JvmStatic
    fun asView(world: World): WorldAccess = world.asView()
}

fun World.asView(): WorldAccess = MixinInterop.requireAccess<WorldAccess>(this)

fun WorldAccess.asHost(): World = MixinInterop.requireHost<World>(this)
